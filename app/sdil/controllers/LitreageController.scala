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
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import sdil.actions.FormAction
import sdil.config.{AppConfig, FormDataCache}
import sdil.forms.FormHelpers
import sdil.models._
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import views.html.softdrinksindustrylevy.register.litreagePage

class LitreageController(val messagesApi: MessagesApi,
                         cache: FormDataCache,
                         formAction: FormAction)
                        (implicit config: AppConfig)
  extends FrontendController with I18nSupport {

  import LitreageController._

  def show(pageName: String) = formAction.async { implicit request =>
    val page = getPage(pageName)

    page.expectedPage(request.formData) match {
      case `page` => Ok(litreagePage(filledForm(page, request.formData), pageName, page.previousPage(request.formData).show))
      case otherPage => Redirect(otherPage.show)
    }
  }

  def validate(pageName: String) = formAction.async { implicit request =>
    val page = getPage(pageName)
    form.bindFromRequest().fold(
      errors => BadRequest(litreagePage(errors, pageName, page.nextPage(request.formData).show)),
      litreage => {
        val updated = update(litreage, request.formData, page)
        cache.cache(request.internalId, updated) map { _ =>
          Redirect(page.nextPage(updated).show)
        }
      }
    )
  }

  private def getPage(pageName: String): MidJourneyPage = pageName match {
    case "packageOwn" => PackageOwnPage
    case "packageCopack" => PackageCopackPage
    case "copackedVolume" => CopackedVolumePage
    case "importVolume" => ImportVolumePage
    case other => throw new IllegalArgumentException(s"Invalid litreage page: $other")
  }

  private def update(litreage: Litreage, formData: RegistrationFormData, page: Page): RegistrationFormData = page match {
    case PackageOwnPage => formData.copy(packageOwn = Some(litreage))
    case PackageCopackPage => formData.copy(packageCopack = Some(litreage))
    case CopackedVolumePage => formData.copy(copackedVolume = Some(litreage))
    case ImportVolumePage => formData.copy(importVolume = Some(litreage))
    case other => throw new IllegalArgumentException(s"Unexpected page name: $other")
  }

  private def filledForm(page: Page, formData: RegistrationFormData): Form[Litreage] = page match {
    case PackageOwnPage => formData.packageOwn.fold(form)(form.fill)
    case PackageCopackPage => formData.packageCopack.fold(form)(form.fill)
    case CopackedVolumePage => formData.copackedVolume.fold(form)(form.fill)
    case ImportVolumePage => formData.importVolume.fold(form)(form.fill)
    case other => throw new IllegalArgumentException(s"Unexpected page name: $other")
  }
}

object LitreageController extends FormHelpers {
  val form: Form[Litreage] = Form(
    mapping(
      "lowerRateLitres" -> litreage,
      "higherRateLitres" -> litreage
    )
    (Litreage.apply)(Litreage.unapply).verifying("error.litreage.zero", _ != Litreage(0, 0)))
}
