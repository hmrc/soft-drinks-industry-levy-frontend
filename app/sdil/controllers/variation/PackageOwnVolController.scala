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
import sdil.controllers.LitreageController
import uk.gov.hmrc.http.cache.client.SessionCache
import views.html.softdrinksindustrylevy.register.litreagePage

class PackageOwnVolController(val messagesApi: MessagesApi,
                              val cache: SessionCache,
                              variationAction: VariationAction)
                             (implicit config: AppConfig)
  extends Journey {

  def show: Action[AnyContent] = variationAction.async { implicit request =>
    val filledForm = request.data.packageOwnVol.fold(LitreageController.form)(LitreageController.form.fill)
    backLink(routes.PackageOwnVolController.show()) map { link =>
      Ok(litreagePage(filledForm, "packageOwnVol", link, Some(submitAction)))
    }
  }

  def submit: Action[AnyContent] = variationAction.async { implicit request =>
    LitreageController.form.bindFromRequest().fold(
      errors =>
        backLink(routes.PackageOwnVolController.show()) map { link =>
          BadRequest(litreagePage(errors, "packageOwnVol", link, Some(submitAction)))
        },
      data => {
        val updated = request.data.copy(packageOwnVol = Some(data))
        cache.cache("variationData", updated) map { _ =>
          Redirect(routes.ProductionSiteVariationController.show())
        }
      }
    )
  }

  lazy val submitAction = routes.PackageOwnVolController.submit()
}
