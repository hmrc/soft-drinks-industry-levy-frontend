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
import sdil.models.Address
import uk.gov.hmrc.play.bootstrap.controller.{BaseController, FrontendController}
import uk.gov.hmrc.play.bootstrap.http.FrontendErrorHandler

class VariationsController(val messagesApi: MessagesApi,
                           sdilConnector: SoftDrinksIndustryLevyConnector,
                           registeredAction: RegisteredAction,
                           errorHandler: FrontendErrorHandler)
                          (implicit config: AppConfig)
  extends FrontendController with I18nSupport {

  def show: Action[AnyContent] = registeredAction.async { implicit request =>
    sdilConnector.retrieveSubscription(request.sdilEnrolment.value) map {
      case Some(s) =>
        Ok(views.html.softdrinksindustrylevy.variations.retrieve_summary(s))
      case None => NotFound(errorHandler.notFoundTemplate)
    }
  }
}
