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

import javax.inject.Inject

import play.api.data.Form
import play.api.data.Forms.single
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.Action
import sdil.config.FormDataCache
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.frontend.controller.FrontendController
import views.html.softdrinksindustrylevy.register

import scala.concurrent.Future

class ImportController @Inject()(val messagesApi: MessagesApi) extends FrontendController with I18nSupport {

  val cache: SessionCache = FormDataCache

  private val importForm = Form(single(
    "isImport" -> booleanMapping))

  def display = Action { implicit request =>
    Ok(register.imports(importForm))
  }

  def submit = Action.async { implicit request =>
      importForm.bindFromRequest.fold(
        formWithErrors => {
          Future.successful(
            BadRequest(register.copacked(formWithErrors)))
        },
        validFormData => {
          if (validFormData){
            Redirect(routes.LitreageController.show("importVolume"))
          }
          else{
            Redirect(routes.StartDateController.displayStartDate())
          }
        })
    }
}
