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
import play.api.mvc.{Action, AnyContent}
import sdil.actions.RegisteredAction
import sdil.config.AppConfig
import sdil.connectors.SoftDrinksIndustryLevyConnector
import sdil.models.variations.VariationData
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import uk.gov.hmrc.play.bootstrap.http.FrontendErrorHandler

import scala.concurrent.Future

class VariationsController(val messagesApi: MessagesApi,
                           sdilConnector: SoftDrinksIndustryLevyConnector,
                           registeredAction: RegisteredAction,
                           errorHandler: FrontendErrorHandler,
                           cache: SessionCache)
                          (implicit config: AppConfig)
  extends FrontendController with I18nSupport {

  def start: Action[AnyContent] = registeredAction.async { implicit request =>
    sdilConnector.retrieveSubscription(request.sdilEnrolment.value) flatMap {
      case Some(s) =>
        val data = VariationData(s)
        cache.cache("variationData", data) map { _ =>
          Redirect(routes.VariationsController.show())
        }
      case None => Future.successful(NotFound(errorHandler.notFoundTemplate))
    }
  }

  def show: Action[AnyContent] = registeredAction.async { implicit request =>
    cache.fetchAndGetEntry[VariationData]("variationData") flatMap  {
      case Some(s) =>
        val updated = s.copy(previousPages = List(routes.VariationsController.show()))
        cache.cache("variationData", updated) map { _ =>
          Ok(views.html.softdrinksindustrylevy.variations.retrieve_summary(s.original, s))
        }
      case None => Future.successful(NotFound(errorHandler.notFoundTemplate))
    }
  }
}