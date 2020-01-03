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

package sdil.controllers.test

import javax.inject.{Inject, Singleton}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import sdil.actions.{AuthorisedAction, RegisteredAction}
import sdil.config.RegistrationFormDataCache
import uk.gov.hmrc.play.bootstrap.controller.FrontendController

import scala.concurrent.ExecutionContext

@Singleton
class TestController @Inject()(
  cache: RegistrationFormDataCache,
  authorisedAction: AuthorisedAction,
  registeredAction: RegisteredAction,
  mcc: MessagesControllerComponents)(implicit ec: ExecutionContext)
    extends FrontendController(mcc) {

  def clearAllS4LEntries(): Action[AnyContent] = authorisedAction.async { implicit request =>
    cache.clear(request.internalId) map { _ =>
      Ok(s"S4L for user id ${request.internalId}-sdil-registration cleared")
    }
  }

  def clearAllS4LEntriesInternal(): Action[AnyContent] = authorisedAction.async { implicit request =>
    cache.clearInternalIdOnly(request.internalId) map { _ =>
      Ok(s"S4L for user id ${request.internalId} cleared")
    }
  }

  def clearById(): Action[AnyContent] = registeredAction.async { implicit request =>
    cache.clearBySdilNumber(request.sdilEnrolment.value) map { _ =>
      Ok(s"S4L for user id ${request.sdilEnrolment.value} cleared")
    }
  }
}
