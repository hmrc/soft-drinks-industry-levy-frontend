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

import play.api.data.Forms.{ boolean, optional, tuple }
import play.api.data.{ Form, Mapping }
import play.api.i18n.Messages
import play.api.mvc._
import play.api.{ Configuration, Logger }
import sdil.config.FrontendAppConfig._
import sdil.config.{ FormDataCache, FrontendAuthConnector }
import sdil.connectors.SoftDrinksIndustryLevyConnector
import sdil.models.DesSubmissionResult
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.retrieve.Retrievals._
import uk.gov.hmrc.auth.core.{ AuthConnector, AuthProviders, AuthorisedFunctions, NoActiveSession }
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.frontend.controller.FrontendController
import views.html.softdrinksindustrylevy._

import scala.concurrent.Future

class SDILController @Inject() (
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

  def testAuth: Action[AnyContent] = authorisedForSDIL { implicit request => implicit utr =>
    Future successful Ok(views.html.helloworld.hello_world(Some(DesSubmissionResult(true))))
  }

  def showPackage(): Action[AnyContent] = Action.async { implicit request =>
    Future successful Ok(register.packagePage(packageForm))
  }

  def submitPackage(): Action[AnyContent] = Action.async { implicit request =>
    packageForm.bindFromRequest.fold(
      formWithErrors => {
        Future(BadRequest(register.packagePage(formWithErrors)))
      },
      validFormData => {
        // TODO save stuff to session or keystore and route appropriately
        Future(Redirect(routes.SDILController.showPackageOwn()))
      })
  }

  def showPackageOwn(): Action[AnyContent] = ???

  private lazy val booleanMapping: Mapping[Boolean] =
    optional(boolean).verifying("sdil.form.radio.error", _.nonEmpty).
      transform(_.getOrElse(false), x => Some(x))

  private val packageForm = Form(
    tuple(
      "isLiable" -> booleanMapping,
      "ownBrands" -> boolean,
      "customers" -> boolean).verifying(
        Messages("sdil.form.check.error"),
        formData => (formData._1 && formData._2 || formData._3) || (!formData._1)))

}