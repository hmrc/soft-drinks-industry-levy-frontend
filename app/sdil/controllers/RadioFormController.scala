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

import play.api.data.Form
import play.api.data.Forms.single
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, Call, Request}
import sdil.config.AppConfig
import sdil.models.Packaging
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import uk.gov.hmrc.play.bootstrap.http.FrontendErrorHandler
import views.html.softdrinksindustrylevy.register

import scala.concurrent.Future

class RadioFormController(val messagesApi: MessagesApi, errorHandler: FrontendErrorHandler, cache: SessionCache)(implicit config: AppConfig)
  extends FrontendController with I18nSupport {

  private val radioButtonForm = Form(single("yesOrNo" -> booleanMapping))

  def display(page: String, trueLink: String, falseLink: String) = Action.async { implicit request =>
    radioBackLink(page) map { backLink =>
      Ok(register.radio_button(radioButtonForm, page, backLink, routes.RadioFormController.submit(page, trueLink, falseLink)))
    }
  }

  def submit(page: String, trueLink: String, falseLink: String) = Action.async { implicit request =>
    radioBackLink(page) flatMap { backLink =>
      radioButtonForm.bindFromRequest().fold(
        formWithErrors => BadRequest(register.radio_button(formWithErrors, page, backLink,
          routes.RadioFormController.submit(page, trueLink, falseLink))),
        radioForm =>
          cache.cache(page, radioForm) flatMap { _ =>
            if (radioForm) Redirect(routes.LitreageController.show(trueLink)) else Redirect(falseLink)
          }
      )
    }
  }

  private def radioBackLink(page: String)(implicit request: Request[_]): Future[Call] = {
    page match {
      case "package-copack-small" => copackSmallBack
      case "copacked" => copackedBack
      case "import" => importBack
      case _ => throw new IllegalArgumentException(s"Invalid page name $page")
    }
  }

  private def copackSmallBack(implicit request: Request[_]): Future[Call] = {
    cache.fetchAndGetEntry[Packaging]("packaging") flatMap {
      case Some(p) if p.customers => routes.LitreageController.show("packageCopack")
      case Some(p) if p.isLiable => routes.LitreageController.show("packageOwn")
      case _ => routes.PackageController.displayPackage()
    }
  }

  private def copackedBack(implicit request: Request[_]): Future[Call] = {
    cache.fetchAndGetEntry[Boolean]("package-copack-small") map {
      case Some(true) => routes.LitreageController.show("packageCopackSmallVol")
      case Some(false) => routes.RadioFormController.display(page = "package-copack-small", trueLink = "packageCopackSmallVol", falseLink = "copacked")
      case _ => routes.PackageController.displayPackage()
    }
  }

  private def importBack(implicit request: Request[_]): Future[Call] = {
    cache.fetchAndGetEntry[Boolean]("copacked") map {
      case Some(true) => routes.LitreageController.show("copackedVolume")
      case Some(false) => routes.RadioFormController.display(page = "copacked", trueLink = "copackedVolume", falseLink = "import")
      case _ => routes.PackageController.displayPackage()
    }
  }

}
