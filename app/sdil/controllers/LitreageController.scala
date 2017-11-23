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
import play.api.data.Forms._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, Request, Result}
import sdil.config.{FormDataCache, FrontendGlobal}
import sdil.models.{Litreage, Packaging}
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.frontend.controller.FrontendController

class LitreageController @Inject()(val messagesApi: MessagesApi) extends FrontendController with I18nSupport {

  val cache: SessionCache = FormDataCache

  def show(pageName: String) = Action { implicit request =>
    Ok(views.html.softdrinksindustrylevy.register.litreagePage(LitreageForm(), pageName))
  }

  def validate(pageName: String) = Action.async { implicit request =>
    LitreageForm().bindFromRequest().fold(
      errors => BadRequest(views.html.softdrinksindustrylevy.register.litreagePage(errors, pageName)),
      data => for {
        _ <- cache.cache(pageName, data)
        packaging <- cache.fetchAndGetEntry[Packaging]("packaging")
      } yield {
        packaging match {
          case Some(p) => nextPageFor(pageName, p)
          case _ => Redirect(routes.SDILController.showPackage())
        }
      }
    )
  }

  private def nextPageFor(page: String, packaging: Packaging)(implicit request: Request[_]): Result = {
    //FIXME question pages need to go in between the litreage pages
    page match {
      case "packageOwn" if packaging.customers => Redirect(routes.LitreageController.show("packageCopack"))
      case "packageOwn" => Redirect(routes.LitreageController.show("packageCopackSmallVol"))
      case "packageCopack" => Redirect(routes.LitreageController.show("packageCopackSmallVol"))
      case "packageCopackSmallVol" => Redirect(routes.LitreageController.show("copackedVolume"))
      case "copackedVolume" => Redirect(routes.LitreageController.show("importVolume"))
      case "importVolume" => Redirect(routes.StartDateController.show())
      case _ => BadRequest(FrontendGlobal.badRequestTemplate)
    }
  }
}

object LitreageForm {
  def apply(): Form[Litreage] = Form(
    mapping(
      "lowerRateLitres" -> positiveInt,
      "higherRateLitres" -> positiveInt)
    (Litreage.apply)(Litreage.unapply))

  private lazy val positiveInt = number.verifying("error.number.negative", _ >= 0)
}
