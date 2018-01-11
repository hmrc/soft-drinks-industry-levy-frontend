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
import sdil.config.{AppConfig, FormDataCache}
import sdil.connectors.SoftDrinksIndustryLevyConnector
import sdil.forms.FormHelpers
import sdil.models.{DetailsCorrect, VerifyPage}
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import uk.gov.voa.play.form.ConditionalMappings.{isEqual, mandatoryIf}
import views.html.softdrinksindustrylevy.{errors, register}

class VerifyController(val messagesApi: MessagesApi, cache: FormDataCache, formAction: FormAction,
                       sdilConnector: SoftDrinksIndustryLevyConnector)(implicit config: AppConfig)
  extends FrontendController with I18nSupport {

  import VerifyController._

  def verify = formAction.async { implicit request =>
    val data = request.formData

    sdilConnector.checkPendingQueue(data.utr) map {
      _.status match {
        case OK | ACCEPTED => Redirect(routes.PendingController.displayPending())
        case _ => VerifyPage.expectedPage(data) match {
          case VerifyPage => Ok(register.verify(
            data.verify.fold(form)(form.fill),
            data.utr,
            data.rosmData.organisation.organisationName,
            data.rosmData.address
          ))
          case otherPage => Redirect(otherPage.show)
        }
      }
    }
  }

  def validate = formAction.async { implicit request =>
    form.bindFromRequest().fold(
      errors => BadRequest(register.verify(errors, request.formData.utr, request.formData.rosmData.organisation.organisationName, request.formData.rosmData.address)),
      detailsCorrect => {
        val updated = request.formData.copy(verify = Some(detailsCorrect))
        cache.cache(request.internalId, updated) map { _ =>
          Redirect(VerifyPage.nextPage(updated).show)
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
