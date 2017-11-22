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

package uk.gov.hmrc.softdrinksindustrylevy.controllers

import javax.inject.Inject

import play.api.Logger
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.retrieve.Retrievals._
import uk.gov.hmrc.auth.core.{AuthConnector, AuthProviders, AuthorisedFunctions, NoActiveSession}
import uk.gov.hmrc.play.frontend.controller.FrontendController
import uk.gov.hmrc.softdrinksindustrylevy.config.FrontendAppConfig._
import uk.gov.hmrc.softdrinksindustrylevy.config.FrontendAuthConnector
import uk.gov.hmrc.softdrinksindustrylevy.connectors.SoftDrinksIndustryLevyConnector
import uk.gov.hmrc.softdrinksindustrylevy.models.DesSubmissionResult

import scala.concurrent.Future

class SDILController @Inject()(val messagesApi: play.api.i18n.MessagesApi, sdilConnector: SoftDrinksIndustryLevyConnector) extends AuthorisedFunctions with FrontendController
  with play.api.i18n.I18nSupport {
  override def authConnector: AuthConnector = FrontendAuthConnector

  def authorisedForSDIL(action: Request[AnyContent] => String => Future[Result]): Action[AnyContent] = {
    Action.async { implicit request =>
      authorised(AuthProviders(GovernmentGateway)).retrieve(saUtr) {
        case Some(utr) => action(request)(utr)
        case _ => Future successful Redirect(routes.SDILController.helloWorld())
      } recover {
        case e: NoActiveSession =>
          Logger.warn(s"Bad person $e")
          Redirect(ggLoginUrl, Map("continue" -> Seq(sdilHomePage), "origin" -> Seq(appName)))
      }
    }
  }

  def helloWorld: Action[AnyContent] = Action.async { implicit request =>
      Future successful Ok(views.html.softdrinksindustrylevy.helloworld.hello_world())
  }

  def testAuth: Action[AnyContent] = authorisedForSDIL { implicit request =>
    implicit utr =>
      Future successful Ok(views.html.softdrinksindustrylevy.helloworld.hello_world(Some(DesSubmissionResult(true))))
  }

}
