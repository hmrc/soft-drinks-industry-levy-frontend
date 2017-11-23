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

import models.Identification
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.Action
import sdil.config.FormDataCache
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.frontend.controller.FrontendController

import scala.concurrent.Future

class IdentifyController @Inject() (val messagesApi: MessagesApi) extends FrontendController with I18nSupport {

  val cache: SessionCache = FormDataCache

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

object IdentifyForm {
  def apply(): Form[Identification] = {
    Form(
      mapping(
        "utr" -> text.verifying("error.utr.invalid", _.matches(utrRegex)),
        "postcode" -> text.verifying("error.postcode.invalid", _.matches(postcodeRegex)))(Identification.apply)(Identification.unapply))
  }

  lazy val utrRegex = """\d{10}"""
  lazy val postcodeRegex = """([Gg][Ii][Rr] 0[Aa]{2})|((([A-Za-z][0-9]{1,2})|(([A-Za-z][A-Ha-hJ-Yj-y][0-9]{1,2})|(([A-Za-z][0-9][A-Za-z])|([A-Za-z][A-Ha-hJ-Yj-y][0-9]?[A-Za-z]))))\s?[0-9][A-Za-z]{2})"""
}
