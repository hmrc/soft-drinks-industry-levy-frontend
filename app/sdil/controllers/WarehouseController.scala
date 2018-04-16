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

package sdil.controllers

import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Call, Result}
import sdil.actions.{FormAction, RegistrationFormRequest}
import sdil.config.{AppConfig, RegistrationFormDataCache}
import sdil.forms.FormHelpers
import sdil.models._
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import uk.gov.voa.play.form.ConditionalMappings.mandatoryIfTrue
import views.html.softdrinksindustrylevy.register.secondaryWarehouse

import scala.concurrent.Future

class WarehouseController(val messagesApi: MessagesApi,
                          cache: RegistrationFormDataCache,
                          formAction: FormAction)
                         (implicit config: AppConfig)
  extends FrontendController with I18nSupport {

  import WarehouseController._

  def show = formAction.async { implicit request =>
    Journey.expectedPage(WarehouseSitesPage) match {
      case WarehouseSitesPage =>
        val fillInitialForm: Form[SecondaryWarehouses] = request.formData.secondaryWarehouses match {
          case Some(Nil) => initialForm.fill(SecondaryWarehouses(Nil, false, None))
          case Some(_) => selectSitesForm
          case None => initialForm
        }
        Ok(secondaryWarehouse(fillInitialForm, secondaryWarehouses, Journey.previousPage(WarehouseSitesPage).show, formTarget))
      case otherPage => Redirect(otherPage.show)
    }
  }

  def addSingleSite = formAction.async { implicit request =>
    validateWith(initialForm)
  }

  def addMultipleSites = formAction.async { implicit request =>
    validateWith(selectSitesForm)
  }

  private def validateWith(form: Form[SecondaryWarehouses])(implicit request: RegistrationFormRequest[_]): Future[Result] = {
    form.bindFromRequest().fold(
      errors => BadRequest(secondaryWarehouse(
        errors,
        secondaryWarehouses,
        Journey.previousPage(WarehouseSitesPage).show,
        formTarget)),
      {
        case SecondaryWarehouses(_, _, Some(addr)) =>
          val updatedSites = request.formData.secondaryWarehouses match {
            case Some(addrs) => Some(addrs :+ addr)
            case _ => Some(Seq(addr))
          }
          cache.cache(request.internalId, request.formData.copy(secondaryWarehouses = updatedSites)) map { _ =>
            Redirect(routes.WarehouseController.show())
          }
        case SecondaryWarehouses(addresses, _, _) =>
          val updated = request.formData.copy(secondaryWarehouses = Some(addresses.map(Address.fromString)))

          cache.cache(request.internalId, updated) map { _ =>
            Redirect(Journey.nextPage(WarehouseSitesPage, updated).show)
          }
      }
    )
  }

  private def secondaryWarehouses(implicit request: RegistrationFormRequest[_]): Seq[Address] = {
    request.formData.secondaryWarehouses.getOrElse(Nil)
  }

  private def formTarget(implicit request: RegistrationFormRequest[_]): Call = {
    secondaryWarehouses match {
      case Nil => routes.WarehouseController.addSingleSite()
      case _ => routes.WarehouseController.addMultipleSites()
    }
  }
}

object WarehouseController extends FormHelpers {

  /* when the user first lands on the page and sees the radio button (and must choose an option) */
  val initialForm: Form[SecondaryWarehouses] = Form(
    mapping(
      "warehouseSites" -> ignored(Seq.empty[String]),
      "addWarehouse" -> mandatoryBoolean,
      "additionalAddress" -> mandatoryIfTrue("addWarehouse", addressMapping)
    )(SecondaryWarehouses.apply)(SecondaryWarehouses.unapply)
  )

  /* after the user has selected at least one warehouse site, and doesn't have to select any of them */
  val selectSitesForm: Form[SecondaryWarehouses] = Form(
    mapping(
      "warehouseSites" -> seq(text),
      "addWarehouse" -> boolean,
      "additionalAddress" -> mandatoryIfTrue("addWarehouse", addressMapping)
    )(SecondaryWarehouses.apply)(SecondaryWarehouses.unapply)
  )

  case class SecondaryWarehouses(addresses: Seq[String], addWarehouse: Boolean, additionalAddress: Option[Address])

}