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
import play.api.data.Forms.single
import play.api.i18n.{I18nSupport, MessagesApi}
import sdil.actions.FormAction
import sdil.config.AppConfig
import sdil.forms.FormHelpers
import sdil.models._
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import uk.gov.hmrc.play.bootstrap.http.FrontendErrorHandler
import views.html.softdrinksindustrylevy.register

class RadioFormController(val messagesApi: MessagesApi,
                          errorHandler: FrontendErrorHandler,
                          cache: SessionCache,
                          formAction: FormAction)
                         (implicit config: AppConfig)
  extends FrontendController with I18nSupport {

  import RadioFormController._

  def display(pageName: String) = formAction.async { implicit request =>
    val page = getPage(pageName)

    page.expectedPage(request.formData) match {
      case `page` => Ok(register.radio_button(form, pageName, page.previousPage(request.formData).show))
      case otherPage => Redirect(otherPage.show)
    }
  }

  def submit(pageName: String) = formAction.async { implicit request =>
    val page = getPage(pageName)
    form.bindFromRequest().fold(
      errors => BadRequest(register.radio_button(errors, pageName, page.previousPage(request.formData).show)),
      choice => {
        val updated = update(choice, request.formData, page)
        cache.cache("formData", updated) map { _ =>
          Redirect(page.nextPage(updated).show)
        }
      }
    )
  }

  private def getPage(pageName: String): MidJourneyPage = pageName match {
    case "package-copack-small" => PackageCopackSmallPage
    case "copacked" => CopackedPage
    case "import" => ImportPage
    case other => throw new IllegalArgumentException(s"Invalid radio form page: $other")
  }

  private def update(choice: Boolean, formData: RegistrationFormData, page: Page): RegistrationFormData = page match {
    case PackageCopackSmallPage => formData.copy(packageCopackSmall = Some(choice), packageCopackSmallVol = None)
    case CopackedPage => formData.copy(copacked = Some(choice), copackedVolume = None)
    case ImportPage => formData.copy(imports = Some(choice), importVolume = None)
    case other => throw new IllegalArgumentException(s"Unexpected page name: $other")
  }

}

object RadioFormController extends FormHelpers {
  val form = Form(single("yesOrNo" -> mandatoryBoolean))
}
