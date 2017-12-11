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

import play.api.data.Forms._
import play.api.data.{Form, Mapping}
import play.api.i18n.{I18nSupport, MessagesApi}
import sdil.actions.FormAction
import sdil.config.AppConfig
import sdil.forms.FormHelpers
import sdil.models.{DetailsCorrect, VerifyPage}
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import uk.gov.voa.play.form.ConditionalMappings.{isEqual, mandatoryIf}

class VerifyController(val messagesApi: MessagesApi, cache: SessionCache, formAction: FormAction)(implicit config: AppConfig)
  extends FrontendController with I18nSupport {

  import VerifyController._

  def verify = formAction.async { implicit request =>
    //FIXME look up UTR, org, address
    VerifyPage.expectedPage(request.formData) match {
      case VerifyPage => Ok(views.html.softdrinksindustrylevy.register.verify(form, "a utr", "an organisation", "an address"))
      case otherPage => Redirect(otherPage.show)
    }
  }

  def validate = formAction.async { implicit request =>
    form.bindFromRequest().fold(
      errors => BadRequest(views.html.softdrinksindustrylevy.register.verify(errors, "a utr", "an organisation", "an address")),
      detailsCorrect => {
        val updated = request.formData.copy(verify = Some(detailsCorrect))
        cache.cache("formData", updated) map { _ =>
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

  def oneOf(options: Seq[String], errorMsg: String): Mapping[String] = {
    //have to use optional, or the framework returns `error.required` when no option is selected
    optional(text).verifying(errorMsg, s => s.exists(options.contains)).transform(_.getOrElse(""), Some.apply)
  }
}
