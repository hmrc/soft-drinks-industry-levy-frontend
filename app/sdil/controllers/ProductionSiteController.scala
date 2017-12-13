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
import play.api.mvc.Action
import sdil.actions.FormAction
import sdil.config.AppConfig
import sdil.forms.FormHelpers
import sdil.models._
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import uk.gov.voa.play.form.ConditionalMappings.mandatoryIfTrue
import views.html.softdrinksindustrylevy.register.productionSite

class ProductionSiteController(val messagesApi: MessagesApi, cache: SessionCache, formAction: FormAction)(implicit config: AppConfig)
  extends FrontendController with I18nSupport {

  import ProductionSiteController.form

  def addSite = formAction.async { implicit request =>
    //FIXME look up address record
    ProductionSitesPage.expectedPage(request.formData) match {
      case ProductionSitesPage => Ok(productionSite(form, fakeAddress, request.formData.productionSites.getOrElse(Nil), previousPage(request.formData).show))
      case otherPage => Redirect(otherPage.show)
    }
  }

  def validate = formAction.async { implicit request =>
    form.bindFromRequest().fold(
      errors => BadRequest(productionSite(errors, fakeAddress, request.formData.productionSites.getOrElse(Nil), previousPage(request.formData).show)),
      {
        case ProductionSite(_, Some(addr)) =>
          val updated = request.formData.productionSites match {
            case Some(addrs) => Some(addrs :+ addr)
            case _ => Some(Seq(addr))
          }
          cache.cache("formData", request.formData.copy(productionSites = updated)) map { _ =>
            Redirect(routes.ProductionSiteController.addSite())
          }
        case _ =>
          cache.cache("formData", request.formData.copy(productionSites = Some(Nil))) map { _ =>
            Redirect(ProductionSitesPage.nextPage(request.formData).show)
          }
      }
    )
  }

  def remove(idx: Int) = formAction.async { implicit request =>
    val updatedSites = request.formData.productionSites map {
      addr => addr.take(idx) ++ addr.drop(idx + 1)
    }
    cache.cache("formData", request.formData.copy(productionSites = updatedSites)) map { _ =>
      Redirect(routes.ProductionSiteController.addSite())
    }
  }

  private def previousPage(formData: RegistrationFormData) = {
    if (LocalDate.now isBefore config.taxStartDate) {
      StartDatePage.previousPage(formData)
    } else {
      ProductionSitesPage.previousPage(formData)
    }
  }

  private lazy val fakeAddress = Address("an address", "somewhere", "", "", "AA11 1AA")
}

object ProductionSiteController extends FormHelpers {
  val form: Form[ProductionSite] = Form(
    mapping(
      "hasOtherSite" -> mandatoryBoolean,
      "otherSiteAddress" -> mandatoryIfTrue("hasOtherSite", addressMapping)
    )(ProductionSite.apply)(ProductionSite.unapply)
  )
}