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

package sdil.controllers

import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent}
import sdil.config.AppConfig
import uk.gov.hmrc.play.bootstrap.controller.FrontendController

class AuthenticationController(val messagesApi: MessagesApi)
                              (implicit config: AppConfig)
  extends FrontendController with I18nSupport {

  def signIn = Action { implicit request =>
    Redirect(config.ggLoginUrl, Map("continue" -> Seq(config.sdilHomePage), "origin" -> Seq(config.appName)))
  }

  def signOut: Action[AnyContent] = Action { implicit request =>
    Redirect(config.signoutRegVarUrl).withNewSession
  }

  def signOutReturns: Action[AnyContent] = Action { implicit request =>
    Redirect(config.signoutReturnsUrl).withNewSession
  }

  def timeIn(referrer: String): Action[AnyContent] = Action { implicit request =>
    Redirect(config.ggLoginUrl, Map("continue" -> Seq(referrer), "origin" -> Seq(config.appName)))
  }

  def timeOut: Action[AnyContent] = Action { implicit request =>
    Ok(views.html.time_out()).withNewSession
  }

  def signOutNoFeedback: Action[AnyContent] = Action { implicit request =>
    Redirect(config.signoutUrlNoFeedback).withNewSession
  }
}