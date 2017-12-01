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

import java.util.UUID

import play.api.Logger
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import sdil.config.AppConfig
import sdil.connectors.SoftDrinksIndustryLevyConnector
import sdil.models._
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.retrieve.Retrievals.saUtr
import uk.gov.hmrc.auth.core.{AuthConnector, AuthProviders, AuthorisedFunctions, NoActiveSession}
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import views.html.softdrinksindustrylevy._

import scala.concurrent.Future

class SDILController(val messagesApi: MessagesApi,
                     val authConnector: AuthConnector,
                     cache: SessionCache,
                     sdilConnector: SoftDrinksIndustryLevyConnector)
                    (implicit config: AppConfig)
  extends AuthorisedFunctions with FrontendController with I18nSupport {

  private def authorisedForSDIL(action: Request[AnyContent] => String => Future[Result]): Action[AnyContent] = {
    Action.async { implicit request =>
      authorised(AuthProviders(GovernmentGateway)).retrieve(saUtr) {
        case Some(utr) => action(request)(utr)
        case _ => Future successful Redirect(config.ggLoginUrl, Map("continue" -> Seq(config.sdilHomePage), "origin" -> Seq(config.appName)))
      } recover {
        case e: NoActiveSession =>
          Logger.warn(s"Bad person $e")
          Redirect(config.ggLoginUrl, Map("continue" -> Seq(config.sdilHomePage), "origin" -> Seq(config.appName)))
      }
    }
  }

  def testAuth: Action[AnyContent] = authorisedForSDIL { implicit request => implicit utr =>
    Future successful Ok(views.html.helloworld.hello_world(Some(DesSubmissionResult(true))))
  }

  def displayPackage(): Action[AnyContent] = Action.async { implicit request =>
    Future successful Ok(register.packagePage(packageForm)).addingToSession(SessionKeys.sessionId -> UUID.randomUUID().toString)
  }

  def submitPackage(): Action[AnyContent] = Action.async { implicit request =>
    packageForm.bindFromRequest.fold(
      formWithErrors => BadRequest(register.packagePage(formWithErrors)),
      validFormData => cache.cache("packaging", validFormData) map { _ =>
        validFormData match {
          case Packaging(_, true, _) => Redirect(routes.LitreageController.show("packageOwn"))
          case Packaging(_, _, true) => Redirect(routes.LitreageController.show("packageCopack"))
          case _ => Redirect(routes.PackageCopackSmallController.display())
        }
      }
    )
  }

  def displayComplete(): Action[AnyContent] = Action.async { implicit request =>
    Ok(register.complete())
  }

  private lazy val packageForm = Form(
    mapping(
      "isLiable" -> booleanMapping,
      "ownBrands" -> boolean,
      "customers" -> boolean
    )(Packaging.apply)(Packaging.unapply)
      .verifying("sdil.form.check.error", p => !p.isLiable || (p.ownBrands || p.customers))
  )

}
