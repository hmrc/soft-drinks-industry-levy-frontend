/*
 * Copyright 2017 HM Revenue & Customs
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
import play.api.data.Forms.mapping
import play.api.i18n.{I18nSupport, MessagesApi}
import sdil.actions.FormAction
import sdil.config.AppConfig
import sdil.forms.FormHelpers
import sdil.models._
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import uk.gov.voa.play.form.ConditionalMappings.mandatoryIfTrue
import views.html.softdrinksindustrylevy.register.secondaryWarehouse

class WarehouseController(val messagesApi: MessagesApi,
                          cache: SessionCache,
                          formAction: FormAction)
                         (implicit config: AppConfig)
  extends FrontendController with I18nSupport {

  import WarehouseController._

  def show = formAction.async { implicit request =>
    WarehouseSitesPage.expectedPage(request.formData) match {
      case WarehouseSitesPage => Ok(secondaryWarehouse(form, request.formData.secondaryWarehouses, previousPage(request.formData).show))
      case otherPage => Redirect(otherPage.show)
    }
  }

  def validate = formAction.async { implicit request =>
    form.bindFromRequest().fold(
      errors => BadRequest(secondaryWarehouse(errors, request.formData.secondaryWarehouses, previousPage(request.formData).show)),
      {
        case SecondaryWarehouse(_, Some(addr)) => {
          val updatedSites = request.formData.secondaryWarehouses :+ addr
          cache.cache("formData", request.formData.copy(secondaryWarehouses = updatedSites)) map { _ =>
            Redirect(routes.WarehouseController.show())
          }
        }
        case _ => Redirect(WarehouseSitesPage.nextPage(request.formData).show)
      }
    )
  }

  def remove(idx: Int) = formAction.async { implicit request =>
    val updatedSites = request.formData.secondaryWarehouses.take(idx) ++ request.formData.secondaryWarehouses.drop(idx + 1)
    cache.cache("formData", request.formData.copy(secondaryWarehouses = updatedSites)) map { _ =>
      Redirect(routes.WarehouseController.show())
    }
  }

  private def previousPage(formData: RegistrationFormData) = WarehouseSitesPage.previousPage(formData) match {
    case StartDatePage if LocalDate.now isBefore config.taxStartDate => StartDatePage.previousPage(formData)
    case other => other
  }
}

object WarehouseController extends FormHelpers {
  val form: Form[SecondaryWarehouse] = Form(
    mapping(
      "hasWarehouse" -> mandatoryBoolean,
      "warehouseAddress" -> mandatoryIfTrue("hasWarehouse", addressMapping)
    )(SecondaryWarehouse.apply)(SecondaryWarehouse.unapply)
  )
}