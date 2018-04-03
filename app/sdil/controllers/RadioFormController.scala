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

    Journey.expectedPage(page) match {
      case `page` => Ok(register.radio_button(
        filledForm(page, request.formData),
        pageName, Journey.previousPage(page).show
      ))
      case otherPage => Redirect(otherPage.show)
    }
  }

  def submit(pageName: String) = formAction.async { implicit request =>
    val page = getPage(pageName)

    form.bindFromRequest().fold(
      errors => BadRequest(register.radio_button(errors, pageName, Journey.previousPage(page).show)),
      choice => {
        val updated = update(choice, request.formData, page)

        cache.cache(request.internalId, updated) map { _ =>
          Redirect(Journey.nextPage(page, updated).show)
        }
      }
    )
  }

  private def getPage(pageName: String): Page = pageName match {
    case "packageCopack" => PackageCopackPage
    case "copacked" => CopackedPage
    case "import" => ImportPage
    case "packageOwnUk" => PackageOwnUkPage
    case other => throw new IllegalArgumentException(s"Invalid radio form page: $other")
  }

  private def update(choice: Boolean, formData: RegistrationFormData, page: Page): RegistrationFormData = page match {
    case PackageCopackPage if choice => formData.copy(packagesForOthers = Some(choice))
    case PackageCopackPage => formData.copy(packagesForOthers = Some(choice), volumeForCustomerBrands = None)
    case CopackedPage => formData.copy(usesCopacker = Some(choice))
    case ImportPage if choice => formData.copy(isImporter = Some(choice))
    case ImportPage =>
      val updated = formData.copy(isImporter = Some(choice), importVolume = None)
      clearUnneededData(updated)
    case PackageOwnUkPage if choice => formData.copy(isPackagingForSelf = Some(choice))
    case PackageOwnUkPage => formData.copy(isPackagingForSelf = Some(choice), volumeForOwnBrand = None)
    case other => throw new IllegalArgumentException(s"Unexpected page name: $other")
  }

  // remove production sites/warehouse sites if user changes from voluntary -> mandatory
  private def clearUnneededData(formData: RegistrationFormData): RegistrationFormData = {
    if (formData.isVoluntary) {
      formData.copy(productionSites = None, secondaryWarehouses = None)
    } else if (!formData.hasPackagingSites) {
      formData.copy(productionSites = None)
    } else {
      formData
    }
  }

  private def filledForm(page: Page, formData: RegistrationFormData): Form[Boolean] = page match {
    case PackageCopackPage => formData.packagesForOthers.fold(form)(form.fill)
    case CopackedPage => formData.usesCopacker.fold(form)(form.fill)
    case ImportPage => formData.isImporter.fold(form)(form.fill)
    case PackageOwnUkPage => formData.isPackagingForSelf.fold(form)(form.fill)
    case other => throw new IllegalArgumentException(s"Unexpected page name: $other")
  }
}

object RadioFormController extends FormHelpers {
  val form = Form(single("yesOrNo" -> mandatoryBoolean))
}
