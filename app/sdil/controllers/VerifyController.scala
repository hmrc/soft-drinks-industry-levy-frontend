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
import play.api.data.Forms._
import play.api.i18n.{I18nSupport, MessagesApi}
import sdil.actions.FormAction
import sdil.config.{AppConfig, RegistrationFormDataCache}
import sdil.connectors.SoftDrinksIndustryLevyConnector
import sdil.forms.FormHelpers
import sdil.models.{DetailsCorrect, Journey, VerifyPage}
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import uk.gov.voa.play.form.ConditionalMappings.{isEqual, mandatoryIf}
import views.html.softdrinksindustrylevy.{errors, register}

class VerifyController(val messagesApi: MessagesApi, cache: RegistrationFormDataCache, formAction: FormAction,
                       sdilConnector: SoftDrinksIndustryLevyConnector)(implicit config: AppConfig)
  extends FrontendController with I18nSupport {

  import VerifyController._

  def show = formAction.async { implicit request =>
    val data = request.formData

    sdilConnector.checkPendingQueue(data.utr) map { res =>
      (res.status, Journey.expectedPage(VerifyPage)) match {
        case (ACCEPTED, _) => Ok(errors.registration_pending(data.utr, data.rosmData.organisationName, data.rosmData.address))
        case (OK, VerifyPage) => Ok(register.verify(
            data.verify.fold(form)(form.fill),
            data.utr,
            data.rosmData.organisationName,
            data.rosmData.address,
            alreadyRegistered = true
          ))
        case (_, VerifyPage) => Ok(register.verify(
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
    form.bindFromRequest().fold(
      errors => BadRequest(register.verify(errors, request.formData.utr, request.formData.rosmData.organisationName, request.formData.rosmData.address)),
      {
        case DetailsCorrect.No => Redirect(routes.AuthenticationController.signOutNoFeedback())
        case detailsCorrect =>
          val updated = request.formData.copy(verify = Some(detailsCorrect))
          cache.cache(request.internalId, updated) map { _ =>
            Redirect(if (!config.uniformRegistrationsEnabled) {
              Journey.nextPage(VerifyPage, updated).show
            } else {
              routes.RegistrationController.index("organisation-type")
            }
          )
        }
      }
    )
  }
}


object VerifyController extends FormHelpers {
  val form: Form[DetailsCorrect] = Form(
    mapping(
      "detailsCorrect" -> oneOf(DetailsCorrect.options, "error.radio-form.choose-option"),
      "alternativeAddress" -> mandatoryIf(isEqual("detailsCorrect", "differentAddress"), addressMapping)
    )(DetailsCorrect.apply)(DetailsCorrect.unapply)
  )

}
