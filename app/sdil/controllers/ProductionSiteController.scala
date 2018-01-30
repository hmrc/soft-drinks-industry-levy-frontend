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
import play.api.data.Forms.mapping
import play.api.i18n.{I18nSupport, MessagesApi}
import sdil.actions.FormAction
import sdil.config.{AppConfig, FormDataCache}
import sdil.forms.FormHelpers
import sdil.models._
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import uk.gov.voa.play.form.ConditionalMappings.mandatoryIfTrue
import views.html.softdrinksindustrylevy.register.productionSite

class ProductionSiteController(val messagesApi: MessagesApi, cache: FormDataCache, formAction: FormAction)(implicit config: AppConfig)
  extends FrontendController with I18nSupport {

  import ProductionSiteController.form

  def addSite = formAction.async { implicit request =>
    ProductionSitesPage.expectedPage(request.formData) match {
      case ProductionSitesPage => Ok(
        productionSite(
          form,
          request.formData.primaryAddress,
          request.formData.productionSites.getOrElse(Nil),
          ProductionSitesPage.previousPage(request.formData).show
        )
      )
      case otherPage => Redirect(otherPage.show)
    }
  }

  def validate = formAction.async { implicit request =>
    form.bindFromRequest().fold(
      errors => BadRequest(
        productionSite(
          errors,
          request.formData.rosmData.address,
          request.formData.productionSites.getOrElse(Nil),
          ProductionSitesPage.previousPage(request.formData).show
        )
      ),
      {
        case ProductionSite(_, Some(addr)) =>
          val updated = request.formData.productionSites match {
            case Some(addrs) => Some(addrs :+ addr)
            case _ => Some(Seq(addr))
          }
          cache.cache(request.internalId, request.formData.copy(productionSites = updated)) map { _ =>
            Redirect(routes.ProductionSiteController.addSite())
          }
        case _ =>
          request.formData.productionSites match {
            case Some(_) => Redirect(ProductionSitesPage.nextPage(request.formData).show)
            case _ => cache.cache(request.internalId, request.formData.copy(productionSites = Some(Nil))) map { _ =>
              Redirect(ProductionSitesPage.nextPage(request.formData).show)
            }
          }
      }
    )
  }

  def remove(idx: Int) = formAction.async { implicit request =>
    val updatedSites = request.formData.productionSites map {
      addr => addr.take(idx) ++ addr.drop(idx + 1)
    }
    cache.cache(request.internalId, request.formData.copy(productionSites = updatedSites)) map { _ =>
      Redirect(routes.ProductionSiteController.addSite())
    }
  }
}

object ProductionSiteController extends FormHelpers {
  val form: Form[ProductionSite] = Form(
    mapping(
      "hasOtherSite" -> mandatoryBoolean,
      "otherSiteAddress" -> mandatoryIfTrue("hasOtherSite", addressMapping)
    )(ProductionSite.apply)(ProductionSite.unapply)
  )
}