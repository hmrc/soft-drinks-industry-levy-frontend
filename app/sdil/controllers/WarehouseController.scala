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

import java.time.LocalDate

import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{I18nSupport, MessagesApi}
import sdil.actions.FormAction
import sdil.config.{AppConfig, FormDataCache}
import sdil.forms.FormHelpers
import sdil.models._
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import uk.gov.voa.play.form.ConditionalMappings.mandatoryIfTrue
import views.html.softdrinksindustrylevy.register.secondaryWarehouse

class WarehouseController(val messagesApi: MessagesApi,
                          cache: FormDataCache,
                          formAction: FormAction)
                         (implicit config: AppConfig)
  extends FrontendController with I18nSupport {

  import WarehouseController._

  def show = formAction.async { implicit request =>
    WarehouseSitesPage.expectedPage(request.formData) match {
      case WarehouseSitesPage => Ok(secondaryWarehouse(form, request.formData.secondaryWarehouses.getOrElse(Nil), previousPage(request.formData).show))
      case otherPage => Redirect(otherPage.show)
    }
  }

  def submit = formAction.async { implicit request =>
    form.bindFromRequest().fold(
      errors => BadRequest(secondaryWarehouse(errors, request.formData.secondaryWarehouses.getOrElse(Nil), previousPage(request.formData).show)),
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
          cache.cache(request.internalId, request.formData.copy(secondaryWarehouses = Some(addresses.map(Address.fromString)))) map { _ =>
            Redirect(WarehouseSitesPage.nextPage(request.formData).show)
          }
      }
    )
  }

  private def previousPage(formData: RegistrationFormData) = WarehouseSitesPage.previousPage(formData) match {
    case StartDatePage if LocalDate.now isBefore config.taxStartDate => StartDatePage.previousPage(formData)
    case other => other
  }
}

object WarehouseController extends FormHelpers {
  val form: Form[SecondaryWarehouses] = Form(
    mapping(
      "warehouseSites" -> seq(text),
      "addWarehouse" -> boolean,
      "additionalAddress" -> mandatoryIfTrue("addWarehouse", addressMapping)
    )(SecondaryWarehouses.apply)(SecondaryWarehouses.unapply)
  )

  case class SecondaryWarehouses(addresses: Seq[String], addWarehouse: Boolean, additionalAddress: Option[Address])

}