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

import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, Call}
import sdil.actions.VariationAction
import sdil.config.AppConfig
import sdil.controllers.ProductionSiteController
import sdil.models.Address
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import views.html.softdrinksindustrylevy.register.productionSite

import scala.concurrent.Future

class ProductionSiteVariationController (val messagesApi: MessagesApi,
                                         cache: SessionCache,
                                         variationAction: VariationAction)
                                        (implicit config: AppConfig)
  extends FrontendController with I18nSupport {

  import ProductionSiteController._
  lazy val previousPage: Call = routes.VariationsController.show()

  def show: Action[AnyContent] = variationAction { implicit request =>
    Ok(
      productionSite(
        form,
        None,
        None,
        request.data.updatedProductionSites,
        previousPage,
        routes.ProductionSiteVariationController.submit()
      )
    )
  }

  def submit: Action[AnyContent] = variationAction.async { implicit request =>
    form.bindFromRequest().fold(
      errors => Future(BadRequest(
        productionSite(
          errors,
          None,
          None,
          request.data.updatedProductionSites,
          previousPage,
          routes.ProductionSiteVariationController.submit()
        )
      )),
      {
        case ProductionSites(_, _, _, true, Some(additionalAddress)) =>
          val updated = request.data.updatedProductionSites :+ additionalAddress
          cache.cache("variationData", request.data.copy(updatedProductionSites= updated)) map { _ =>
            Redirect(routes.ProductionSiteVariationController.show())
          }

        case p =>
          val addresses = p.additionalSites.map(Address.fromString)
          val updated = request.data.copy(updatedProductionSites= addresses)
          cache.cache("variationData", updated) map { _ =>
            Redirect(previousPage)
          }
      }
    )
  }

}

