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
import play.api.mvc.Result
import sdil.actions.{FormAction, RegistrationFormRequest}
import sdil.config.{AppConfig, FormDataCache}
import sdil.forms.FormHelpers
import sdil.models.{Litreage, MidJourneyPage, PackageCopackSmallPage, PackageCopackSmallVolPage}
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import views.html.softdrinksindustrylevy.register.litreagePage

import scala.concurrent.Future

class PackageCopackSmallVolumeController(val messagesApi: MessagesApi,
                                         cache: FormDataCache,
                                         formAction: FormAction)
                                        (implicit config: AppConfig)
  extends FrontendController with I18nSupport {

  import PackageCopackSmallVolumeController.form

  private lazy val page: MidJourneyPage = PackageCopackSmallVolPage

  def show = formAction.async { implicit request =>
    withPackageCopack { pcv =>
      page.expectedPage(request.formData) match {
        case `page` => Ok(litreagePage(
          request.formData.packageCopackSmallVol.fold(form(pcv))(form(pcv).fill),
          "packageCopackSmallVol",
          page.previousPage(request.formData).show,
          nextLink = Some(routes.PackageCopackSmallVolumeController.validate())
        ))
        case otherPage => Redirect(otherPage.show)
      }
    }
  }

  def validate = formAction.async { implicit request =>
    withPackageCopack { pcv =>
      form(pcv).bindFromRequest().fold(
        errors => BadRequest(litreagePage(
          errors,
          "packageCopackSmallVol",
          page.previousPage(request.formData).show,
          nextLink = Some(routes.PackageCopackSmallVolumeController.validate())
        )),
        litreage => {
          val updated = request.formData.copy(packageCopackSmallVol = Some(litreage))
          cache.cache(request.internalId, updated) map { _ =>
            Redirect(page.nextPage(updated).show)
          }
        }
      )
    }
  }

  private def withPackageCopack(body: Litreage => Future[Result])(implicit request: RegistrationFormRequest[_]): Future[Result] = {
    request.formData.packageCopack match {
      case Some(pcv) => body(pcv)
      case None => Redirect(PackageCopackSmallPage.show)
    }
  }
}

object PackageCopackSmallVolumeController extends FormHelpers {
  def form(packageCopackVol: Litreage): Form[Litreage] = Form(
    mapping(
      "lowerRateLitres" -> litreage.verifying("error.copack-small.lower-greater-than-total-lower", _ <= packageCopackVol.atLowRate),
      "higherRateLitres" -> litreage.verifying("error.copack-small.higher-greater-than-total-higher", _ <= packageCopackVol.atHighRate)
    )(Litreage.apply)(Litreage.unapply).verifying("error.litreage.zero", _ != Litreage(0, 0))
  )
}
