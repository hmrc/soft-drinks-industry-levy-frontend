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

package sdil.controllers.variation

import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, Call}
import sdil.actions.VariationAction
import sdil.config.AppConfig
import sdil.controllers.ContactDetailsController
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import views.html.softdrinksindustrylevy.register
import views.html.softdrinksindustrylevy.variations

import scala.concurrent.Future

class ContactDetailsVariationController(val messagesApi: MessagesApi,
                                        cache: SessionCache,
                                        variationAction: VariationAction)
                                       (implicit config: AppConfig)
  extends FrontendController with I18nSupport {

  lazy val formTarget: Call = routes.ContactDetailsVariationController.submit()
  lazy val previousPage: Call = routes.VariationsController.show()

  def show: Action[AnyContent] = variationAction { implicit request =>
    Ok(
      register.contact_details(
        ContactDetailsController.form.fill(request.data.updatedContactDetails),
        previousPage,
        formTarget,
        variations = true
      )
    )
  }

  def submit: Action[AnyContent] = variationAction.async { implicit request =>
    ContactDetailsController.form.bindFromRequest().fold(
      formWithErrors => Future.successful(BadRequest(register.contact_details(
        formWithErrors,
        previousPage, formTarget))),
      details => cache.cache("variationData", request.data.copy(updatedContactDetails = details)) map { _ =>
        Redirect(routes.ContactDetailsVariationController.confirm())
      }
    )
  }

  def confirm: Action[AnyContent] = variationAction { implicit request =>
    Ok(variations.retrieve_summary_contact_details(request.data))
  }
}

