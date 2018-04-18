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

import play.api.i18n.MessagesApi
import play.api.mvc.{Action, AnyContent}
import sdil.actions.VariationAction
import sdil.config.AppConfig
import sdil.controllers.RadioFormController
import uk.gov.hmrc.http.cache.client.SessionCache
import views.html.softdrinksindustrylevy.register.radio_button

class ImportsController(val messagesApi: MessagesApi,
                        val cache: SessionCache,
                        variationAction: VariationAction)
                       (implicit config: AppConfig)
  extends Journey {

  def show: Action[AnyContent] = variationAction.async { implicit request =>
    val filledForm = RadioFormController.form.fill(request.data.imports)
    backLink(routes.ImportsController.show()) map { link =>
      Ok(radio_button(filledForm, "import", link, submitAction))
    }
  }

  def submit: Action[AnyContent] = variationAction.async { implicit request =>
    RadioFormController.form.bindFromRequest().fold(
      errors =>
        backLink(routes.ImportsController.show()) map { link =>
          BadRequest(radio_button(errors, "import", link, submitAction))
        },
      imports => {
        val updated = request.data.copy(imports = imports)
        cache.cache("variationData", updated) map { _ =>
          if (imports) {
            Redirect(routes.ImportsVolController.show())
          } else {
            Redirect(routes.VariationsController.show())
          }
        }
      }
    )
  }

  lazy val submitAction = routes.ImportsController.submit()
}
