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

import play.api.data.Form
import play.api.data.Forms.{mapping, text}
import play.api.data.validation.{Constraint, Invalid, Valid}
import play.api.i18n.{I18nSupport, MessagesApi}
import sdil.actions.AuthorisedAction
import sdil.config.AppConfig
import sdil.forms.FormHelpers
import sdil.models.{Identification, RegistrationFormData}
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.bootstrap.controller.FrontendController

import scala.concurrent.Future

class IdentifyController(val messagesApi: MessagesApi, cache: SessionCache, authorisedAction: AuthorisedAction)(implicit config: AppConfig)
  extends FrontendController with I18nSupport {

  import IdentifyController.form

  def identify = authorisedAction { implicit request =>
    Ok(views.html.softdrinksindustrylevy.register.identify(form))
  }

  def validate = authorisedAction.async { implicit request =>
    form.bindFromRequest().fold(
      errors => Future.successful(BadRequest(views.html.softdrinksindustrylevy.register.identify(errors))),
      identification => cache.cache("formData", RegistrationFormData(identification)) map { _ =>
        Redirect(routes.VerifyController.verify())
      }
    )
  }
}

object IdentifyController extends FormHelpers {
  val form: Form[Identification] = Form(
    mapping(
      "utr" -> text.verifying(Constraint { x: String => x match {
        case "" => Invalid("error.utr.required")
        case utr if utr.exists(!_.isDigit) => Invalid("error.utr.invalid")
        case utr if utr.length != 10 => Invalid("error.utr.length")
        case _ => Valid
      }}),
      "postcode" -> postcode
    )(Identification.apply)(Identification.unapply)
  )
}