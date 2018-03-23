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
import play.api.data.Forms._
import play.api.data.validation.{Constraint, Constraints, Invalid, Valid}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent}
import sdil.actions.FormAction
import sdil.config.{AppConfig, FormDataCache}
import sdil.forms.FormHelpers
import sdil.models._
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import views.html.softdrinksindustrylevy.register

import scala.concurrent.Future

class ContactDetailsController(val messagesApi: MessagesApi, cache: FormDataCache, formAction: FormAction)
                              (implicit config: AppConfig)
  extends FrontendController with I18nSupport {

  import ContactDetailsController._

  def show: Action[AnyContent] = formAction.async { implicit request =>
    Journey.expectedPage(ContactDetailsPage) match {
      case ContactDetailsPage =>
        val filledForm = request.formData.contactDetails.fold(form)(form.fill)
        Ok(register.contact_details(filledForm, Journey.previousPage(ContactDetailsPage).show))
      case otherPage => Redirect(otherPage.show)
    }
  }

  def submit: Action[AnyContent] = formAction.async { implicit request =>
    form.bindFromRequest().fold(
      formWithErrors => Future.successful(BadRequest(register.contact_details(formWithErrors, Journey.previousPage(ContactDetailsPage).show))),
      details => cache.cache(request.internalId, request.formData.copy(contactDetails = Some(details))) map { _ =>
        Redirect(routes.DeclarationController.submit())
      }
    )
  }
}

object ContactDetailsController extends FormHelpers {
  val form = Form(
    mapping(
      "fullName" -> text.verifying(Constraint { x: String =>
        x match {
          case "" => Invalid("error.fullName.required")
          case name if name.length > 40 => Invalid("error.fullName.over")
          case name if !name.matches("^[a-zA-Z &`\\-\\'\\.^]{1,40}$") =>
            Invalid("error.fullName.invalid")
          case _ => Valid
        }
      }),
      "position" -> text.verifying(Constraint { x: String =>
        x match {
          case "" => Invalid("error.position.required")
          case position if position.length > 155 => Invalid("error.position.over")
          case position if !position.matches("^[a-zA-Z &`\\-\\'\\.^]{1,155}$") =>
            Invalid("error.position.invalid")
          case _ => Valid
        }
      }),
      "phoneNumber" -> text.verifying(Constraint { x: String =>
        x match {
          case "" => Invalid("error.phoneNumber.required")
          case phone if phone.length > 24 => Invalid("error.phoneNumber.over")
          case phone if !phone.matches("^[A-Z0-9 )/(\\-*#+]{1,24}$") =>
            Invalid("error.phoneNumber.invalid")
          case _ => Valid
        }
      }),
      "email" -> text
        .verifying("error.email.over", _.length <= 132)
        .verifying(combine(required("email"), Constraints.emailAddress))
    )(ContactDetails.apply)(ContactDetails.unapply)
  )
}