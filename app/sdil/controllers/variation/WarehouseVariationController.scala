/*
 * Copyright 2018 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package sdil.controllers.variation

import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, Call, Result}
import sdil.actions.{VariationAction, VariationRequest}
import sdil.config.AppConfig
import sdil.controllers.WarehouseController.{SecondaryWarehouses, initialForm, selectSitesForm}
import sdil.models.Address
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import views.html.softdrinksindustrylevy.register.secondaryWarehouse

import scala.concurrent.Future

class WarehouseVariationController (val messagesApi: MessagesApi,
                                    cache: SessionCache,
                                    variationAction: VariationAction)
                                   (implicit config: AppConfig)
  extends FrontendController with I18nSupport {

  lazy val previousPage: Call = routes.VariationsController.show()

  def show: Action[AnyContent] = variationAction { implicit request =>
    val fillInitialForm: Form[SecondaryWarehouses] = warehouses match {
      case Nil => initialForm.fill(SecondaryWarehouses(Nil, false, None))
      case _ => selectSitesForm
    }
    Ok(
      secondaryWarehouse(
        fillInitialForm,
        warehouses,
        previousPage,
        formTarget
      )
    )
  }


  def addSingleSite: Action[AnyContent] = variationAction.async { implicit request =>
    validateWith(initialForm)
  }

  def addMultipleSites: Action[AnyContent]  = variationAction.async { implicit request =>
    validateWith(selectSitesForm)
  }

  private def validateWith(form: Form[SecondaryWarehouses])(implicit request: VariationRequest[_]): Future[Result] = {
    form.bindFromRequest().fold(
      errors => Future(BadRequest(
        secondaryWarehouse(
          errors,
          warehouses,
          previousPage,
          formTarget
        )
      )),
      {
        case SecondaryWarehouses(_, _, Some(addr)) =>
          val updatedSites = warehouses match {
            case addrs if addrs.nonEmpty => addrs :+ addr
            case _ => Seq(addr)
          }
          cache.cache("variationData", request.data.copy(updatedWarehouseSites= updatedSites)) map { _ =>
            Redirect(routes.WarehouseVariationController.show())
          }
        case SecondaryWarehouses(addresses, _, _) =>
          val updated = request.data.copy(updatedWarehouseSites = addresses.map(Address.fromString))

          cache.cache("variationData", updated) map { _ =>
            Redirect(previousPage)
          }
      }
    )
  }

  private def warehouses(implicit request: VariationRequest[_]): Seq[Address] = {
    request.data.updatedWarehouseSites
  }

  private def formTarget(implicit request: VariationRequest[_]): Call = {
    warehouses match {
      case Nil => routes.WarehouseVariationController.addSingleSite()
      case _ => routes.WarehouseVariationController.addMultipleSites()
    }
  }
}
