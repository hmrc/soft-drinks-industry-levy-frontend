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

import java.util.UUID
import javax.inject.Inject

import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.Action
import sdil.config.AppConfig
import sdil.forms.IdentifyForm
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.bootstrap.controller.FrontendController

import scala.concurrent.Future

class IdentifyController @Inject()(val messagesApi: MessagesApi, cache: SessionCache)(implicit config: AppConfig)
  extends FrontendController with I18nSupport {

  def identify = Action { implicit request =>
    //FIXME session ID should be initialised at the start of the journey
    Ok(views.html.softdrinksindustrylevy.register.identify(IdentifyForm())).addingToSession(SessionKeys.sessionId -> UUID.randomUUID().toString)
  }

  def validate = Action.async { implicit request =>
    IdentifyForm().bindFromRequest().fold(
      errors => Future.successful(BadRequest(views.html.softdrinksindustrylevy.register.identify(errors))),
      data => cache.cache("identify", data) map { _ =>
        Redirect(routes.VerifyController.verify())
      })
  }
}
