/*
 * Copyright 2017 HM Revenue & Customs
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
import sdil.config.AppConfig
import sdil.forms.VerifyForm
import sdil.models.DetailsCorrect
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.bootstrap.controller.FrontendController

class VerifyController(val messagesApi: MessagesApi, cache: SessionCache)(implicit config: AppConfig)
  extends FrontendController with I18nSupport {

  def verify = Action { implicit request =>
    //FIXME look up UTR, org, address
    Ok(views.html.softdrinksindustrylevy.register.verify(VerifyForm(), "a utr", "an organisation", "an address"))
  }

  def validate = Action.async { implicit request =>
    VerifyForm().bindFromRequest().fold(
      errors => BadRequest(views.html.softdrinksindustrylevy.register.verify(errors, "a utr", "an organisation", "an address")),
      data => cache.cache("verifiedDetails", data) map { _ =>
        if (data == DetailsCorrect.No) {
          Redirect(routes.IdentifyController.identify())
        } else {
          Redirect(routes.PackageController.displayPackage())
        }
      }
    )
  }
}
