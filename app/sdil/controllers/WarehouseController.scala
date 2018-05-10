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

import play.api.data.Forms._
import play.api.data.{Form, FormError, Mapping}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, Call, Result}
import sdil.actions.{FormAction, RegistrationFormRequest}
import sdil.config.{AppConfig, RegistrationFormDataCache}
import sdil.forms.{FormHelpers, MappingWithExtraConstraint}
import sdil.models._
import sdil.models.backend.{Site, UkAddress}
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import uk.gov.voa.play.form.ConditionalMappings.mandatoryIfTrue
import views.html.softdrinksindustrylevy.register.secondaryWarehouse

import scala.concurrent.Future

class WarehouseController(val messagesApi: MessagesApi,
                          cache: RegistrationFormDataCache,
                          formAction: FormAction)
                         (implicit config: AppConfig)
  extends FrontendController with I18nSupport with SiteRef {

  import WarehouseController._

  def show: Action[AnyContent] = formAction.async { implicit request =>
    Journey.expectedPage(WarehouseSitesPage) match {
      case WarehouseSitesPage =>
        val fillInitialForm: Form[Sites] = request.formData.secondaryWarehouses match {
          case Some(Nil) => initialForm.fill(Sites(Nil, addAddress = false, None, None))
          case Some(_) => form
          case None => initialForm
        }
        Ok(secondaryWarehouse(fillInitialForm, secondaryWarehouses, Journey.previousPage(WarehouseSitesPage).show, formTarget))
      case otherPage => Redirect(otherPage.show)
    }
  }

  val addSingleSite: Action[AnyContent] = formAction.async { implicit request =>
    validateWith(initialForm)
  }

  val addMultipleSites: Action[AnyContent] = formAction.async { implicit request =>
    validateWith(form)
  }

  private def validateWith(form: Form[Sites])(implicit request: RegistrationFormRequest[_]): Future[Result] = {
    form.bindFromRequest().fold(
      errors => BadRequest(secondaryWarehouse(
        errors,
        secondaryWarehouses,
        Journey.previousPage(WarehouseSitesPage).show,
        formTarget)),
      {
        case Sites(_, _, tradingName, Some(addr)) =>
          val updatedSites = request.formData.secondaryWarehouses match {
            case Some(addrs) if addrs.nonEmpty =>
              addrs :+ Site(
                UkAddress.fromAddress(addr),
                None,
                tradingName,
                None
              )
            case _ => Seq(Site(
              UkAddress.fromAddress(addr),
              None,
              tradingName,
              None
            ))
          }
          cache.cache(request.internalId, request.formData.copy(secondaryWarehouses = Some(updatedSites))) map { _ =>
            Redirect(routes.WarehouseController.show())
          }
        case Sites(addresses, _, _, _) =>
          val updated = request.formData.copy(secondaryWarehouses = Some(addresses))
          cache.cache(request.internalId, updated) map { _ =>
            Redirect(Journey.nextPage(WarehouseSitesPage, updated).show)
          }
      }
    )
  }

  private def secondaryWarehouses(implicit request: RegistrationFormRequest[_]): Seq[Site] = {
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

  val form: Form[Sites] = Form(mapping(
    "additionalSites" -> seq(siteJsonMapping),
    "addAddress" -> boolean,
    "tradingName" -> mandatoryIfTrue("addAddress", tradingNameMapping),
    "additionalAddress" -> mandatoryIfTrue("addAddress", addressMapping)
  )(Sites.apply)(Sites.unapply))

}