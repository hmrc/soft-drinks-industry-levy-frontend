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
import play.api.mvc.{Action, AnyContent, Request, Result}
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.retrieve.Retrievals._
import uk.gov.hmrc.auth.core.{AuthProviders, AuthorisedFunctions, NoActiveSession}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.frontend.controller.FrontendController
import uk.gov.hmrc.softdrinksindustrylevy.config.FrontendAppConfig._
import uk.gov.hmrc.softdrinksindustrylevy.config.FrontendAuthConnector
import uk.gov.hmrc.softdrinksindustrylevy.models.DesSubmissionResult

import scala.concurrent.Future

class SDILController @Inject()(val messagesApi: play.api.i18n.MessagesApi) extends AuthorisedFunctions with FrontendController
  with play.api.i18n.I18nSupport {
  override def authConnector: FrontendAuthConnector.type = FrontendAuthConnector

  def authorisedAction(body: String => Future[Result])(implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] = {
    authorised(AuthProviders(GovernmentGateway)).retrieve(saUtr) {
      case Some(utr) => body(utr)
      case _ => Future successful Redirect(routes.HelloWorldController.backendRetrieve())
    } recover {
      case e: NoActiveSession =>
        Logger.warn(s"Bad person $e")
        Redirect(ggLoginUrl, Map("continue" -> Seq(sdilHomePage), "origin" -> Seq(appName)))
    }
  }

  def testAuth = Action.async { implicit request =>
    authorisedAction { utr =>
      Future successful Ok(views.html.softdrinksindustrylevy.helloworld.hello_world(Some(DesSubmissionResult(true))))
    }
  }

}
