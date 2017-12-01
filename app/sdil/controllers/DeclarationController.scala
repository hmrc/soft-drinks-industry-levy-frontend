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

import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent}
import sdil.config.AppConfig
import sdil.models.{ContactDetails, Identification}
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import views.html.softdrinksindustrylevy.register

import scala.concurrent.Future

class DeclarationController(val messagesApi: MessagesApi, cache: SessionCache)(implicit config: AppConfig)
  extends FrontendController with I18nSupport {


  def displayDeclaration: Action[AnyContent] = Action.async { implicit request =>
    Future successful Ok(register.
      declaration(
        Identification("foo", "bar"),
        ContactDetails(
          fullName = "Nick Karaolis",
          position = "Scala Ninja",
          phoneNumber = "x directory",
          email = "nick.karaolis@wouldn'tyouliketoknow.com"
        )
      ))
  }

  def submitDeclaration(): Action[AnyContent] = Action.async { implicit request =>
    // TODO hit the backend to create subscription
    Redirect(routes.SDILController.displayComplete())
  }

}
