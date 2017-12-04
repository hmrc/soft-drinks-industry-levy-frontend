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

import play.api.data.Form
import play.api.data.Forms.{mapping, number}
import play.api.data.validation.{Constraint, Invalid, Valid, ValidationError}
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc.{Action, AnyContent, Request}
import sdil.config.AppConfig
import sdil.models.sdilmodels._
import sdil.models.{Packaging, StartDate}
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.bootstrap.controller.FrontendController

import scala.util.Try

class StartDateController(val messagesApi: MessagesApi, cache: SessionCache)(implicit config: AppConfig)
  extends FrontendController with I18nSupport {

  def displayStartDate: Action[AnyContent] = Action.async { implicit request =>
    if (LocalDate.now isBefore config.taxStartDate) {
      for {
        _ <- cache.cache("start-date", config.taxStartDate)
        packaging <- cache.fetchAndGetEntry[Packaging]("packaging")
      } yield {
        packaging match {
          case Some(p) if p.isLiable => Redirect(routes.ProductionSiteController.addSite())
          case Some(p) => Redirect(routes.WarehouseController.secondaryWarehouse())
          case None => Redirect(routes.PackageController.displayPackage())
        }
      }
    } else {
      getBackLink map { link =>
        Ok(views.html.softdrinksindustrylevy.register.start_date(startDateForm, link))
      }
    }
  }

  def submitStartDate: Action[AnyContent] = Action.async { implicit request =>
    validateStartDate(startDateForm.bindFromRequest()).fold(
      errors => getBackLink map { link => BadRequest(views.html.softdrinksindustrylevy.register.start_date(errors, link)) },
      data => {
        cache.cache("start-date", data) flatMap { _ =>
          cache.fetchAndGetEntry[Packaging]("packaging") map {
            case Some(Packaging(true, _, _)) => Redirect(routes.ProductionSiteController.addSite())
            case _ => Redirect(routes.WarehouseController.secondaryWarehouse())
          }
        }
      }
    )
  }

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
        case b if b.toString.length > 4 => Seq(ValidationError(Messages("error.start-date.year-too-high")))
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
      else if (LocalDate.of(year, month, day) isBefore config.taxStartDate)
        form.withError("", Messages("error.start-date.date-too-low"))
      else form
    }
  }

  def isValidDate(day: Int, month: Int, year: Int): Boolean = {
    Try {
      val fmt = new SimpleDateFormat("dd/MM/yyyy")
      fmt.setLenient(false)
      fmt.parse(s"$day/$month/$year")
    }.isSuccess
  }

  private def getBackLink(implicit request: Request[_]) = {
    cache.fetchAndGetEntry[Boolean]("import") map {
      case Some(true) => routes.LitreageController.show("importVolume")
      case Some(false) => routes.ImportController.display()
      case None => routes.PackageController.displayPackage()
    }
  }
}
