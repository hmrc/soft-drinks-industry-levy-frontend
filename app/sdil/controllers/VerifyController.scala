/*
 * Copyright 2020 HM Revenue & Customs
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
import ltbs.play.scaffold.SdilComponents.addressMapping
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.MessagesControllerComponents
import sdil.actions.FormAction
import sdil.config.{AppConfig, RegistrationFormDataCache}
import sdil.connectors.SoftDrinksIndustryLevyConnector
import sdil.forms.FormHelpers
import sdil.models.{DetailsCorrect, Journey, VerifyPage}
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
  views: Views)(implicit config: AppConfig, ec: ExecutionContext)
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

}
