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

import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.Action
import sdil.actions.FormAction
import sdil.config.{AppConfig, FormDataCache}
import sdil.models._
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import views.html.softdrinksindustrylevy.register.registration_not_required

class RegistrationTypeController(val messagesApi: MessagesApi,
                                 cache: FormDataCache,
                                 formAction: FormAction)
                                (implicit appConfig: AppConfig)
  extends FrontendController with I18nSupport {

  def continue = formAction.async { implicit request =>
    RegistrationTypePage.expectedPage(request.formData) match {
      case RegistrationTypePage => registrationType(request.formData) match {
        case RegistrationNotRequired => Redirect(routes.RegistrationTypeController.registrationNotRequired())
        case MandatoryOnly => Redirect(routes.StartDateController.displayStartDate())
        case VoluntaryOnly => NotImplemented(views.html.defaultpages.todo())
        case MandatoryAndVoluntary => NotImplemented(views.html.defaultpages.todo())
      }
      case other => Redirect(other.show)
    }
  }

  def registrationNotRequired = Action { implicit request =>
    Ok(registration_not_required())
  }

  private def registrationType(formData: RegistrationFormData): RegistrationType = {
    def total(p: Option[Litreage], b: Option[Litreage]) = p.fold[BigDecimal](0)(_.total) + b.fold[BigDecimal](0)(_.total)

    def isMandatory(p: Option[Litreage], b: Option[Litreage], c: Boolean, i: Boolean) = total(p, b) >= 1000000 || c || i
    def isVoluntary(p: Option[Litreage], b: Option[Litreage]) = total(p, b) < 1000000 && b.exists(_.total != 0)

    (formData.packageOwn, formData.copackedVolume, formData.packaging, formData.imports) match {
      case (p, b, Some(Packaging(_, _, c)), Some(i))
        if isMandatory(p, b, c, i) && isVoluntary(p, b) => MandatoryAndVoluntary
      case (p, b, Some(Packaging(_, _, c)), Some(i)) if isMandatory(p, b, c, i) => MandatoryOnly
      case (p, b, _, _) if isVoluntary(p, b) => VoluntaryOnly
      case _ => RegistrationNotRequired
    }
  }

  sealed trait RegistrationType

  case object MandatoryOnly extends RegistrationType

  case object VoluntaryOnly extends RegistrationType

  case object MandatoryAndVoluntary extends RegistrationType

  case object RegistrationNotRequired extends RegistrationType
}
