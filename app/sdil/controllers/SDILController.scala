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

import java.text.SimpleDateFormat
import java.time.LocalDate
import javax.inject.Inject

import play.api.Logger
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.{Constraint, Invalid, Valid, ValidationError}
import play.api.i18n.Messages
import play.api.mvc._
import sdil.config.FrontendAppConfig._
import sdil.config.{FormDataCache, FrontendAuthConnector}
import sdil.connectors.SoftDrinksIndustryLevyConnector
import sdil.models.sdilmodels._
import sdil.models._
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
    if (LocalDate.now isBefore StartDateForm.taxStartDate){
      Future.successful(Ok(views.html.softdrinksindustrylevy.register.identify(StartDateForm.startDateForm)))
      //TODO change identify to next view
    }
    else {
      Future.successful(Ok(views.html.softdrinksindustrylevy.register.start_date(StartDateForm.startDateForm)))
    }
  }

  def submitStartDate: Action[AnyContent] = Action.async { implicit request =>

    StartDateForm.validateStartDate(StartDateForm.startDateForm.bindFromRequest()).fold(
      errors => Future.successful(BadRequest(views.html.softdrinksindustrylevy.register.start_date(errors))),
      data => cache.cache("start-date", data) map { _ =>
        Redirect(routes.SDILController.displaySites())
      })
  }

  def displaySites() = TODO

  object StartDateForm {
    def startDateForm: Form[StartDate] = Form(
      mapping(
        "startDateDay" -> number.verifying(startDayConstraint),
        "startDateMonth" -> number.verifying(startMonthConstraint),
        "startDateYear" -> number.verifying(startYearConstraint)
      )(StartDate.apply)(StartDate.unapply)
    )

    val startDayConstraint: Constraint[Int] = Constraint {
      day =>
        val errors = day match {
          case a if a <= 0 => Seq(ValidationError(Messages("error.start-date.day-too-low")))
          case b if b > 31 => Seq(ValidationError(Messages("error.start-date.day-too-high")))
          case _ => Nil
        }
        if (errors.isEmpty) Valid else Invalid(errors)
    }
    val startMonthConstraint: Constraint[Int] = Constraint {
      day =>
        val errors = day match {
          case a if a <= 0 => Seq(ValidationError(Messages("error.start-date.month-too-low")))
          case b if b > 12 => Seq(ValidationError(Messages("error.start-date.month-too-high")))
          case _ => Nil
        }
        if (errors.isEmpty) Valid else Invalid(errors)
    }
    val startYearConstraint: Constraint[Int] = Constraint {
      day =>
        val errors = day match {
          case a if a < 2017 => Seq(ValidationError(Messages("error.start-date.year-too-low")))
          case b if b.toString.length > 4=> Seq(ValidationError(Messages("error.start-date.year-too-high")))
          case _ => Nil
        }
        if (errors.isEmpty) Valid else Invalid(errors)
    }

    def validateStartDate(form: Form[StartDate]): Form[StartDate] = {
      if (form.hasErrors) form else {
        val day = form.get.startDateDay
        val month = form.get.startDateMonth
        val year = form.get.startDateYear
        if (!isValidDate(day, month, year)) form.withError("", Messages("error.start-date.date-invalid"))
        else if (LocalDate.of(year, month, day) isAfter LocalDate.now) form.withError("", Messages("error.start-date.date-too-high"))
        else if (LocalDate.of(year, month, day) isBefore taxStartDate)
          form.withError("", Messages("error.start-date.date-too-low"))
        else form
      }
    }

    val taxStartDate = LocalDate.parse("2018-04-06")

    def isValidDate(day: Int, month: Int, year: Int): Boolean = {
      try {
        val fmt = new SimpleDateFormat("dd/MM/yyyy")
        fmt.setLenient(false)
        fmt.parse(s"$day/$month/$year")
        true
      } catch {
        case e: Exception => false
      }
    }
  }

}

