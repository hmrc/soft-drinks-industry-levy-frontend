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
import play.api.mvc.{Action, AnyContent}
import sdil.actions.FormAction
import sdil.config.{AppConfig, FormDataCache}
import sdil.models._
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import views.html.softdrinksindustrylevy.register.registration_not_required

class RegistrationTypeController(val messagesApi: MessagesApi,
                                 cache: FormDataCache,
                                 formAction: FormAction)
                                (implicit appConfig: AppConfig)
  extends FrontendController with I18nSupport {

  def continue: Action[AnyContent] = formAction.async { implicit request =>
    RegistrationTypePage.expectedPage(request.formData) match {
      case RegistrationTypePage if request.formData.isNotAllowedToRegister =>
        Redirect(routes.RegistrationTypeController.registrationNotRequired())
      // ensure production sites/warehouses are not stored if they aren't needed
      case RegistrationTypePage if request.formData.isVoluntary =>
        cache.cache(request.internalId, request.formData.copy(
          productionSites = None,
          secondaryWarehouses = None
        )
        ) map { _ =>
          Redirect(routes.StartDateController.show())
        }
      case RegistrationTypePage if !request.formData.hasPackagingSites =>
        cache.cache(request.internalId, request.formData.copy(productionSites = Some(Nil))) map { _ =>
          Redirect(routes.StartDateController.show())
        }
      case RegistrationTypePage => Redirect(routes.StartDateController.show())
      case other => Redirect(other.show)
    }
  }

  def registrationNotRequired: Action[AnyContent] = Action { implicit request =>
    Ok(registration_not_required())
  }
}
