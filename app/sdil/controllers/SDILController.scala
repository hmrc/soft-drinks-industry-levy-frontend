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

import play.api.Logger
import play.api.data.Form
import play.api.data.Forms.{mapping, text}
import play.api.mvc._
import sdil.config.FrontendAppConfig._
import sdil.config.{FormDataCache, FrontendAuthConnector}
import sdil.connectors.SoftDrinksIndustryLevyConnector
import sdil.models._
import sdil.models.sdilmodels._
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.retrieve.Retrievals._
import uk.gov.hmrc.auth.core.{AuthConnector, AuthProviders, AuthorisedFunctions, NoActiveSession}
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.frontend.controller.FrontendController

import scala.concurrent.Future

class SDILController @Inject()(
                                val messagesApi: play.api.i18n.MessagesApi,
                                sdilConnector: SoftDrinksIndustryLevyConnector) extends AuthorisedFunctions with FrontendController
  with play.api.i18n.I18nSupport {

  override def authConnector: AuthConnector = FrontendAuthConnector

  val cache: SessionCache = FormDataCache

  private def authorisedForSDIL(action: Request[AnyContent] => String => Future[Result]): Action[AnyContent] = {
    Action.async { implicit request =>
      authorised(AuthProviders(GovernmentGateway)).retrieve(saUtr) {
        case Some(utr) => action(request)(utr)
        case _ => Future successful Redirect(ggLoginUrl, Map("continue" -> Seq(sdilHomePage), "origin" -> Seq(appName)))
      } recover {
        case e: NoActiveSession =>
          Logger.warn(s"Bad person $e")
          Redirect(ggLoginUrl, Map("continue" -> Seq(sdilHomePage), "origin" -> Seq(appName)))
      }
    }
  }

  def testAuth: Action[AnyContent] = authorisedForSDIL { implicit request =>
    implicit utr =>
      Future successful Ok(views.html.helloworld.hello_world(Some(DesSubmissionResult(true))))
  }

  def displayStartDate: Action[AnyContent] = Action.async { implicit request =>
    Future.successful(Ok(views.html.softdrinksindustrylevy.register.start_date(startDateForm)))
  }

  def submitStartDate: Action[AnyContent] = Action.async { implicit request =>
    startDateForm.bindFromRequest().fold(
      errors => Future.successful(BadRequest(views.html.softdrinksindustrylevy.register.start_date(formWitherrors))),
      data => cache.cache("start-date", data) map { _ =>
        Redirect(routes.SDILController.displaySites())
      })
  }

  def displaySites() = TODO

  private val startDateForm = Form(
    mapping(
      "day" -> text.verifying("error.day.invalid", _.matches(dayRegex)),
      "month" -> text.verifying("error.month.invalid", _.matches(monthRegex)),
      "year" -> text.verifying("error.year.invalid", _.matches(yearRegex)))(StartDate.apply)(StartDate.unapply))

  lazy val dayRegex = """"(0[1-9]|[12]\d|3[01])"""
  lazy val monthRegex = """"^(0?[1-9]|1[012])$""""
  lazy val yearRegex = """^\d{4}$"""

}

