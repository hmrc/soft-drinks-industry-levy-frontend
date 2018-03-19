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
import sdil.models.SmallProducerConfirmPage
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import views.html.softdrinksindustrylevy.register

class SmallProducerConfirmController(val messagesApi: MessagesApi,
                                     cache: FormDataCache,
                                     formAction: FormAction)(implicit config: AppConfig)
  extends FrontendController with I18nSupport {

  def show: Action[AnyContent] = formAction.async { implicit request =>
    SmallProducerConfirmPage.expectedPage(request.formData) match {
      case SmallProducerConfirmPage => Ok(register.smallProducerConfirm(SmallProducerConfirmPage.previousPage(request.formData).show))
      case otherPage => Redirect(otherPage.show)
    }
  }

  def submit(): Action[AnyContent] = formAction.async { implicit request =>
    val updated = request.formData.copy(confirmedSmallProducer = Some(true))
    cache.cache(request.internalId, updated) map { _ =>
      Redirect(routes.StartDateController.show())
    }
  }
}