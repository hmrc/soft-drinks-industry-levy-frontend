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
import views.html.softdrinksindustrylevy.register.litreagePage

import scala.concurrent.Future

class ContactDetailsController(val messagesApi: MessagesApi, cache: FormDataCache, formAction: FormAction)(implicit config: AppConfig)
  extends FrontendController with I18nSupport {

  import ContactDetailsController._

  def displayContactDetails: Action[AnyContent] = formAction.async { implicit request =>
    ContactDetailsPage.expectedPage(request.formData) match {
      case ContactDetailsPage => Ok(register.contact_details(request.formData.contactDetails.fold(form)(form.fill), previousPage(request.formData).show))
      case otherPage => Redirect(otherPage.show)
    }
  }

  def submitContactDetails: Action[AnyContent] = formAction.async { implicit request =>
    form.bindFromRequest().fold(
      formWithErrors => Future successful BadRequest(register.contact_details(formWithErrors, previousPage(request.formData).show)),
      details => cache.cache(request.internalId, request.formData.copy(contactDetails = Some(details))) map { _ =>
        Redirect(routes.DeclarationController.displayDeclaration())
      }
    )
  }
  private def previousPage(formData: RegistrationFormData) = {
    ContactDetailsPage.previousPage(formData) match {
      case StartDatePage if LocalDate.now isBefore config.taxStartDate => StartDatePage.previousPage(formData)
      case other => other
    }
  }
}

object ContactDetailsController extends FormHelpers {
  val form = Form(
    mapping(
      "fullName" -> text
        .verifying("error.fullName.over", _.length <= 40)
        .verifying("error.fullName.required", _.nonEmpty),
      "position" -> text
        .verifying("error.position.over", _.length <= 155)
        .verifying("error.position.required", _.nonEmpty),
      "phoneNumber" -> text.verifying(Constraint { x: String =>
        x match {
          case "" => Invalid("error.phoneNumber.required")
          case number if number.length > 24 => Invalid("error.phoneNumber.over")
          case number if !number.matches("^[0-9 ()+--]{1,24}$") => Invalid("error.phoneNumber.invalid")
          case _ => Valid
        }
      }),
      "email" -> text
        .verifying("error.email.over", _.length <= 132)
        .verifying(combine(required("email"), Constraints.emailAddress))
    )(ContactDetails.apply)(ContactDetails.unapply)
  )
}