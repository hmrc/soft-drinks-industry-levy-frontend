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

import play.api.data.Forms._
import play.api.data.validation.{Constraint, Constraints, Invalid, Valid}
import play.api.data.{Form, Mapping}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.Action
import sdil.config.AppConfig
import sdil.connectors.ContactFrontendConnector
import uk.gov.hmrc.play.bootstrap.controller.FrontendController

import scala.concurrent.Future

class RegisterForBetaController(val messagesApi: MessagesApi,
                                contactFrontend: ContactFrontendConnector)
                               (implicit config: AppConfig)
  extends FrontendController with I18nSupport {

  def show = Action { implicit request =>
    Ok(views.html.softdrinksindustrylevy.betaSignUp.register_for_beta(registrationForm))
  }

  def submit = Action.async { implicit request =>
    registrationForm.bindFromRequest().fold(
      errors => Future.successful(BadRequest(views.html.softdrinksindustrylevy.betaSignUp.register_for_beta(errors))),
      formData => contactFrontend.submitBetaRegistrationRequest(formData) map { _ =>
        Ok(views.html.softdrinksindustrylevy.betaSignUp.beta_registration_confirmed())
      }
    )
  }

  lazy val registrationForm: Form[BetaRegistration] = Form(
    //total limit of all misc/comment fields is 2000
    mapping(
      formField("businessName", maxLength = 100),
      "utr" -> text.verifying(Constraint { s: String =>
        s.trim match {
          case "" => Invalid("error.utr.required")
          case s if s.length != 10 => Invalid("error.utr.length")
          case _ if !s.matches("""^[0-9]{10}$""") => Invalid("error.utr.invalid")
          case _ => Valid
        }
      }),
      formField("fullName", maxLength = 70),
      "email" -> text.verifying(Constraint { s: String =>
        s.trim match {
          case "" => Invalid("error.email.required")
          case _ if s.length > 255 => Invalid("error.email.length", 255)
          case _ => Constraints.emailAddress(s)
        }
      }),
      "phoneNumber" -> text.verifying(Constraint { s: String =>
        s.trim match {
          case "" => Invalid("error.phoneNumber.required")
          case s if s.length > 20 => Invalid("error.phoneNumber.length")
          case s if !s.matches("""^[0-9()+--]*$""") => Invalid("error.phoneNumber.invalid")
          case _ => Valid
        }
      }
      )
    )(BetaRegistration.apply)(BetaRegistration.unapply)
  )

  private def formField(fieldName: String, maxLength: Int): (String, Mapping[String]) = {
    fieldName -> text.verifying(Constraint { s: String =>
      s.trim match {
        case "" => Invalid(s"error.$fieldName.required")
        case _ if s.length > maxLength => Invalid(s"error.$fieldName.length", maxLength)
        case _ => Valid
      }
    })
  }
}

case class BetaRegistration(businessName: String, utr: String, fullName: String, email: String, phoneNumber: String)
