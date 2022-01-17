/*
 * Copyright 2022 HM Revenue & Customs
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

import ltbs.play.scaffold.GdsComponents.oneOf
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.MessagesControllerComponents
import sdil.actions.FormAction
import sdil.config.{AppConfig, RegistrationFormDataCache}
import sdil.connectors.SoftDrinksIndustryLevyConnector
import sdil.forms.FormHelpers
import sdil.models.{Address, DetailsCorrect, Journey, VerifyPage}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import uk.gov.voa.play.form.ConditionalMappings.{isEqual, mandatoryIf}
import views.Views
import views.softdrinksindustrylevy.errors.Errors

import scala.concurrent.ExecutionContext

class VerifyController(
  override val messagesApi: MessagesApi,
  cache: RegistrationFormDataCache,
  formAction: FormAction,
  sdilConnector: SoftDrinksIndustryLevyConnector,
  mcc: MessagesControllerComponents,
  errors: Errors,
  views: Views
)(implicit config: AppConfig, ec: ExecutionContext)
    extends FrontendController(mcc) with I18nSupport {

  import VerifyController._

  def show = formAction.async { implicit request =>
    val data = request.formData

    sdilConnector.checkPendingQueue(data.utr) map { res =>
      (res.status, Journey.expectedPage(VerifyPage)) match {
        case (ACCEPTED, _) =>
          Ok(errors.registrationPending(data.utr, data.rosmData.organisationName, data.rosmData.address))
        case (OK, VerifyPage) =>
          Ok(
            views.verify(
              data.verify.fold(form)(form.fill),
              data.utr,
              data.rosmData.organisationName,
              data.rosmData.address,
              alreadyRegistered = true
            ))
        case (_, VerifyPage) =>
          Ok(
            views.verify(
              data.verify.fold(form)(form.fill),
              data.utr,
              data.rosmData.organisationName,
              data.rosmData.address
            ))
        case (_, otherPage) => Redirect(otherPage.show)
      }
    }
  }

  def submit = formAction.async { implicit request =>
    form
      .bindFromRequest()
      .fold(
        errors =>
          BadRequest(
            views.verify(
              errors,
              request.formData.utr,
              request.formData.rosmData.organisationName,
              request.formData.rosmData.address)), {
          case DetailsCorrect.No => Redirect(routes.AuthenticationController.signOutNoFeedback())
          case detailsCorrect =>
            val updated = request.formData.copy(verify = Some(detailsCorrect))
            cache.cache(request.internalId, updated) map { _ =>
              Redirect(routes.RegistrationController.index("organisation-type"))
            }
        }
      )
  }
}

object VerifyController extends FormHelpers {
  val form: Form[DetailsCorrect] = Form(
    mapping(
      "detailsCorrect"     -> oneOf(DetailsCorrect.options, "sdil.verify.error.choose-option"),
      "alternativeAddress" -> mandatoryIf(isEqual("detailsCorrect", "differentAddress"), addressMapping)
    )(DetailsCorrect.apply)(DetailsCorrect.unapply)
  )

  import play.api.data.validation.{Constraint, Constraints, Invalid, Valid}
  import play.api.data.Forms._
  import play.api.data._
  lazy val addressMapping: Mapping[Address] = mapping(
    "line1"    -> mandatoryAddressLine("line1"),
    "line2"    -> mandatoryAddressLine("line2"),
    "line3"    -> optionalAddressLine("line3"),
    "line4"    -> optionalAddressLine("line4"),
    "postcode" -> postcode
  )(Address.apply)(Address.unapply)

  private def mandatoryAddressLine(key: String): Mapping[String] =
    text.transform[String](_.trim, s => s).verifying(combine(required(key), optionalAddressLineConstraint(key)))

  private def optionalAddressLine(key: String): Mapping[String] =
    text.transform[String](_.trim, s => s).verifying(optionalAddressLineConstraint(key))

  private def optionalAddressLineConstraint(key: String): Constraint[String] = Constraint {
    case a if !a.matches("""^[A-Za-z0-9 \-,.&'\/]*$""") => Invalid(s"error.$key.invalid")
    case b if b.length > 35                             => Invalid(s"error.$key.over")
    case _                                              => Valid
  }

  def postcode: Mapping[String] = {
    val postcodeRegex = "^[A-Z]{1,2}[0-9][0-9A-Z]?\\s?[0-9][A-Z]{2}$|BFPO\\s?[0-9]{1,5}$"
    val specialRegex = """^[A-Za-z0-9 _]*[A-Za-z0-9][A-Za-z0-9 _]*$"""

    text
      .transform[String](_.toUpperCase.trim, identity)
      .verifying(Constraint { x: String =>
        x match {
          case ""                               => Invalid("error.postcode.empty")
          case pc if !pc.matches(specialRegex)  => Invalid("error.postcode.special")
          case pc if !pc.matches(postcodeRegex) => Invalid("error.postcode.invalid")
          case _                                => Valid
        }
      })
  }

}
