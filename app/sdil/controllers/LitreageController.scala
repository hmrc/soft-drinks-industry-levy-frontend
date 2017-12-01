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
import play.api.mvc.{Action, Call, Request, Result}
import sdil.config.AppConfig
import sdil.forms.LitreageForm
import sdil.models.Packaging
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import uk.gov.hmrc.play.bootstrap.http.FrontendErrorHandler

class LitreageController(val messagesApi: MessagesApi, errorHandler: FrontendErrorHandler, cache: SessionCache)(implicit config: AppConfig)
  extends FrontendController with I18nSupport {

  def show(pageName: String) = Action.async { implicit request =>
    cache.fetchAndGetEntry[Packaging]("packaging") map {
      case Some(p) => Ok(views.html.softdrinksindustrylevy.register.litreagePage(LitreageForm(), pageName, backLinkFor(pageName, p)))
      case None => Redirect(routes.SDILController.displayPackage())
    }
  }

  def validate(pageName: String) = Action.async { implicit request =>
    cache.fetchAndGetEntry[Packaging]("packaging") flatMap {
      case Some(p) => LitreageForm().bindFromRequest().fold(
        errors => BadRequest(views.html.softdrinksindustrylevy.register.litreagePage(errors, pageName, backLinkFor(pageName, p))),
        data => cache.cache(pageName, data) map { _ =>
          nextPageFor(pageName, p)
        }
      )
      case None => Redirect(routes.SDILController.displayPackage())
    }
  }

  private def nextPageFor(page: String, packaging: Packaging)(implicit request: Request[_]): Result = {
    page match {
      case "packageOwn" if packaging.customers => Redirect(routes.LitreageController.show("packageCopack"))
      case "packageOwn" => Redirect(routes.PackageCopackSmallController.display())
      case "packageCopack" => Redirect(routes.PackageCopackSmallController.display())
      case "packageCopackSmallVol" => Redirect(routes.CopackedController.display())
      case "copackedVolume" => Redirect(routes.ImportController.display())
      case "importVolume" => Redirect(routes.StartDateController.displayStartDate())
      case _ => BadRequest(errorHandler.badRequestTemplate)
    }
  }

  private def backLinkFor(page: String, packaging: Packaging)(implicit request: Request[_]): Call = {
    page match {
      case "packageOwn" => routes.SDILController.displayPackage()
      case "packageCopack" if packaging.ownBrands => routes.LitreageController.show("packageOwn")
      case "packageCopack" => routes.SDILController.displayPackage()
      case "packageCopackSmallVol" => routes.PackageCopackSmallController.display()
      case "copackedVolume" => routes.CopackedController.display()
      case "importVolume" => routes.ImportController.display()
      case _ => throw new IllegalArgumentException(s"Invalid page name $page")
    }
  }
}
