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

import play.api.data.Form
import play.api.data.Forms.{mapping, text}
import play.api.data.validation.{Constraint, Invalid, Valid}
import play.api.i18n.{I18nSupport, MessagesApi}
import sdil.actions.{AuthorisedAction, EnrolmentRequest}
import sdil.config.AppConfig
import sdil.connectors.SoftDrinksIndustryLevyConnector
import sdil.forms.FormHelpers
import sdil.models.{Address, Identification, RegistrationFormData}
import uk.gov.hmrc.auth.core.EnrolmentIdentifier
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import views.html.softdrinksindustrylevy.register

class IdentifyController(val messagesApi: MessagesApi,
                         cache: SessionCache,
                         authorisedAction: AuthorisedAction,
                         softDrinksIndustryLevyConnector: SoftDrinksIndustryLevyConnector)(implicit config: AppConfig)
  extends FrontendController with I18nSupport {

  import IdentifyController.form

  def show = authorisedAction { implicit request =>
    Ok(register.identify(form))
  }

  def getUtr = authorisedAction.async { implicit request =>
    getUtrFromEnrolments match {
      case Some(utr) => softDrinksIndustryLevyConnector.getRosmRegistration(utr.value) flatMap {
        case Some(reg) => cache.cache("formData", RegistrationFormData(reg, utr.value)) map { _ =>
          Redirect(routes.VerifyController.verify())
        }
        case None => Redirect(routes.IdentifyController.show())
      }
      case None => Redirect(routes.IdentifyController.show())
    }
  }

  private def getUtrFromEnrolments(implicit request: EnrolmentRequest[_]): Option[EnrolmentIdentifier] = {
    request.enrolments.getEnrolment("IR-CT").orElse(request.enrolments.getEnrolment("IR-SA")).flatMap(_.getIdentifier("UTR"))
  }

  def validate = authorisedAction.async { implicit request =>
    form.bindFromRequest().fold(
      errors => BadRequest(register.identify(errors)),
      identification => softDrinksIndustryLevyConnector.getRosmRegistration(identification.utr) flatMap {
        case Some(reg) if postcodesMatch(reg.address, identification) =>
          cache.cache("formData", RegistrationFormData(reg, identification.utr)) map { _ =>
            Redirect(routes.VerifyController.verify())
          }
        case _ => BadRequest(register.identify(form.withError("utr", "error.utr.no-record")))
      }
    )
  }

  private def postcodesMatch(rosmAddress: Address, identification: Identification) = {
    rosmAddress.postcode.replaceAll(" ", "").equalsIgnoreCase(identification.postcode.replaceAll(" ", ""))
  }
}

object IdentifyController extends FormHelpers {
  val form: Form[Identification] = Form(
    mapping(
      "utr" -> text.verifying(Constraint { x: String => x match {
        case "" => Invalid("error.utr.required")
        case utr if utr.exists(!_.isDigit) => Invalid("error.utr.invalid")
        case utr if utr.length != 10 => Invalid("error.utr.length")
          //TODO No business partner record exists for the UTR = This is not a valid UTR
        case _ => Valid
      }}),
      "postcode" -> postcode
      //TODO Postcode doesn't match the business partner record = This UTR and postcode do not match our records
    )(Identification.apply)(Identification.unapply)
  )
}