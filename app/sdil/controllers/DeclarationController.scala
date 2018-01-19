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

import java.time.LocalDateTime

import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent}
import sdil.actions.FormAction
import sdil.config.{AppConfig, FormDataCache}
import sdil.connectors.SoftDrinksIndustryLevyConnector
import sdil.models.{ContactDetailsPage, SubmissionData}
import sdil.models.backend.Subscription
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import views.html.softdrinksindustrylevy.register

class DeclarationController(val messagesApi: MessagesApi,
                            cache: FormDataCache,
                            formAction: FormAction,
                            sdilConnector: SoftDrinksIndustryLevyConnector,
                            keystore: SessionCache)
                           (implicit config: AppConfig)
  extends FrontendController with I18nSupport {

  def displayDeclaration: Action[AnyContent] = formAction.async { implicit request =>
    request.formData.contactDetails match {
      case Some(details) => Ok(register.declaration(request.formData))
      case None => Redirect(routes.ContactDetailsController.displayContactDetails())
    }
  }

  def submitDeclaration(): Action[AnyContent] = formAction.async { implicit request =>
    Subscription.fromFormData(request.formData) match {
      case Some(s) => for {
        _ <- sdilConnector.submit(s, request.formData.rosmData.safeId)
        _ <- keystore.cache("submissionData", SubmissionData(s.contact.email, LocalDateTime.now))
        _ <- cache.clear(request.internalId)
      } yield {
        Redirect(routes.CompleteController.displayComplete())
      }
      case None => Redirect(ContactDetailsPage.expectedPage(request.formData).show)
    }
  }

}
