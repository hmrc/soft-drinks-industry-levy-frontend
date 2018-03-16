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
import play.api.data.Forms.single
import play.api.i18n.{I18nSupport, MessagesApi}
import sdil.actions.FormAction
import sdil.config.{AppConfig, FormDataCache}
import sdil.forms.FormHelpers
import sdil.models._
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import uk.gov.hmrc.play.bootstrap.http.FrontendErrorHandler
import views.html.softdrinksindustrylevy.register

class RadioFormController(val messagesApi: MessagesApi,
                          errorHandler: FrontendErrorHandler,
                          cache: FormDataCache,
                          formAction: FormAction)
                         (implicit config: AppConfig)
  extends FrontendController with I18nSupport {

  import RadioFormController._

  def show(pageName: String) = formAction.async { implicit request =>
    val page = getPage(pageName)

    page.expectedPage(request.formData) match {
      case `page` => Ok(register.radio_button(
        filledForm(page, request.formData),
        pageName, page.previousPage(request.formData).show
      ))
      case otherPage => Redirect(otherPage.show)
    }
  }

  def submit(pageName: String) = formAction.async { implicit request =>
    val page = getPage(pageName)
    form.bindFromRequest().fold(
      errors => BadRequest(register.radio_button(errors, pageName, page.previousPage(request.formData).show)),
      choice => {
        val updated = update(choice, request.formData, page)
        cache.cache(request.internalId, updated) map { _ =>
          Redirect(page.nextPage(updated).show)
        }
      }
    )
  }

  private def getPage(pageName: String): MidJourneyPage = pageName match {
    case "packageCopack" => PackageCopackPage
    case "packageCopackSmall" => PackageCopackSmallPage
    case "copacked" => CopackedPage
    case "import" => ImportPage
    case "packageOwnUk" => PackageOwnUkPage
    case other => throw new IllegalArgumentException(s"Invalid radio form page: $other")
  }

  private def update(choice: Boolean, formData: RegistrationFormData, page: Page): RegistrationFormData = page match {
    case PackageCopackPage if choice => formData.copy(packagesForOthers = Some(choice))
    case PackageCopackPage => formData.copy(packagesForOthers = Some(choice), volumeForCustomerBrands = None)
    case PackageCopackSmallPage if choice => formData.copy(packagesForSmallProducers = Some(choice))
    //clear volumes if user changes their answer from "Yes" to "No"
    case PackageCopackSmallPage => formData.copy(packagesForSmallProducers = Some(choice), volumeForSmallProducers = None)
    case CopackedPage if choice => formData.copy(usesCopacker = Some(choice))
    case CopackedPage => formData.copy(usesCopacker = Some(choice), volumeByCopackers = None)
    case ImportPage if choice => formData.copy(isImporter = Some(choice))
    case ImportPage => formData.copy(isImporter = Some(choice), importVolume = None)
    case PackageOwnUkPage if choice => formData.copy(isPackagingForSelf = Some(choice))
    case PackageOwnUkPage => formData.copy(isPackagingForSelf = None)
    case other => throw new IllegalArgumentException(s"Unexpected page name: $other")
  }

  private def filledForm(page: Page, formData: RegistrationFormData): Form[Boolean] = page match {
    case PackageCopackPage => formData.packagesForOthers.fold(form)(form.fill)
    case PackageCopackSmallPage => formData.packagesForSmallProducers.fold(form)(form.fill)
    case CopackedPage => formData.usesCopacker.fold(form)(form.fill)
    case ImportPage => formData.isImporter.fold(form)(form.fill)
    case PackageOwnUkPage => formData.isPackagingForSelf.fold(form)(form.fill)
    case other => throw new IllegalArgumentException(s"Unexpected page name: $other")



  }

}

object RadioFormController extends FormHelpers {
  val form = Form(single("yesOrNo" -> mandatoryBoolean))
}
