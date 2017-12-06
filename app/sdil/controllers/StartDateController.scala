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

import play.api.data.{Form, Mapping}
import play.api.data.Forms._
import play.api.data.validation.{Constraint, Invalid, Valid, ValidationError}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, Request}
import sdil.config.AppConfig
import sdil.forms.FormHelpers
import sdil.models.sdilmodels._
import sdil.models.{Packaging, StartDate}
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.bootstrap.controller.FrontendController

import scala.util.Try

class StartDateController(val messagesApi: MessagesApi, cache: SessionCache)(implicit config: AppConfig)
  extends FrontendController with I18nSupport {

  import StartDateController._

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
        Ok(views.html.softdrinksindustrylevy.register.start_date(form, link))
      }
    }
  }

  def submitStartDate: Action[AnyContent] = Action.async { implicit request =>
    form.bindFromRequest().fold(
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

  private def getBackLink(implicit request: Request[_]) = {
    cache.fetchAndGetEntry[Boolean]("import") map {
      case Some(true) => routes.LitreageController.show("importVolume")
      case Some(false) => routes.RadioFormController.display(page = "import", trueLink = "importVolume", falseLink = "start-date")
      case None => routes.PackageController.displayPackage()
    }
  }
}

object StartDateController extends FormHelpers {

  def form(implicit appConfig: AppConfig): Form[LocalDate] = Form(
    single(
      "startDate" -> startDate
        .verifying("error.start-date.in-future", !_.isAfter(LocalDate.now))
        .verifying("error.start-date.before-tax-start", !_.isBefore(appConfig.taxStartDate))
    )
  )

  lazy val startDate: Mapping[LocalDate] = tuple(
    "day" -> numeric("day").verifying("error.start-day.invalid", d => d > 0 && d <= 31),
    "month" -> numeric("month").verifying("error.start-month.invalid", d => d > 0 && d <= 12),
    "year" -> numeric("year").verifying("error.start-year.invalid", d => d >= 1900 && d < 2100)
  ).verifying("error.date.invalid", x => x match { case (d, m, y) => Try(LocalDate.of(y, m, d)).isSuccess } )
    .transform({ case (d, m, y) => LocalDate.of(y, m, d) }, d => (d.getDayOfMonth, d.getMonthValue, d.getYear))

  def numeric(key: String): Mapping[Int] = text
    .verifying(s"error.$key.required", _.nonEmpty)
    .verifying("error.number", v => v.isEmpty || Try(v.toInt).isSuccess)
    .transform[Int](_.toInt, _.toString)
}
