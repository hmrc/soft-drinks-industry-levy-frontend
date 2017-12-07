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
import play.api.data.Forms._
import play.api.data.validation.{Constraint, Constraints, Invalid, Valid}
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc.{Action, AnyContent}
import sdil.config.AppConfig
import sdil.forms.FormHelpers
import sdil.models.ContactDetails
import sdil.models.sdilmodels._
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import views.html.softdrinksindustrylevy.register

import scala.concurrent.Future

class ContactDetailsController(val messagesApi: MessagesApi, cache: SessionCache)(implicit config: AppConfig)
  extends FrontendController with I18nSupport {

  import ContactDetailsController._

  def displayContactDetails: Action[AnyContent] = Action.async { implicit request =>
    Future successful Ok(register.contact_details(form))
  }

  def submitContactDetails: Action[AnyContent] = Action.async { implicit request =>
    form.bindFromRequest().fold(
      formWithErrors => Future successful BadRequest(register.contact_details(formWithErrors)),
      d => cache.cache("contact-details", d) map { _ =>
        Redirect(routes.DeclarationController.displayDeclaration())
      })
  }

}

object ContactDetailsController extends FormHelpers {
  val form = Form(
    mapping(
      "fullName" -> text
        .verifying("error.fullName.required", _.nonEmpty)
        .verifying("error.fullName.length", _.length <= 40),
      "position" -> text
        .verifying("error.position.required", _.nonEmpty)
        .verifying("error.position.length", _.length <= 155),
      "phoneNumber" -> text.verifying(Constraint { x: String => x match {
        case "" => Invalid("error.phoneNumber.required")
        case name if name.length > 24 => Invalid("error.phoneNumber.length")
        case name if !name.matches("^[0-9 ()+--]{1,24}$") => Invalid("error.phoneNumber.invalid")
        case _ => Valid
      }}),
      "email" -> text
        .verifying("error.email.length", _.length <= 132)
        .verifying(combine(required("email"), Constraints.emailAddress))
    )(ContactDetails.apply)(ContactDetails.unapply)
  )
}