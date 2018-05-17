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

import java.time.LocalDate

import play.api.data.Forms._
import play.api.data.{Form, Mapping}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent}
import sdil.actions.FormAction
import sdil.config.{AppConfig, RegistrationFormDataCache}
import sdil.forms.FormHelpers
import sdil.models.{Journey, StartDatePage}
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import views.html.softdrinksindustrylevy.register.start_date

import scala.util.Try

class StartDateController(val messagesApi: MessagesApi, cache: RegistrationFormDataCache, formAction: FormAction)(implicit config: AppConfig)
  extends FrontendController with I18nSupport {

  import StartDateController._

  def show: Action[AnyContent] = formAction.async { implicit request =>
    Journey.expectedPage(StartDatePage) match {
      case StartDatePage =>
        val filled = request.formData.startDate.fold(form)(form.fill)
        Ok(start_date(filled, Journey.previousPage(StartDatePage).show))
      case otherPage => Redirect(otherPage.show)
    }
  }

  def submit: Action[AnyContent] = formAction.async { implicit request =>
    form.bindFromRequest().fold(
      errors => BadRequest(start_date(errors, Journey.previousPage(StartDatePage).show)),
      startDate => {
        val updated = request.formData.copy(startDate = Some(startDate))
        cache.cache(request.internalId, updated) map { _ =>
          Redirect(Journey.nextPage(StartDatePage, updated).show)
        }
      }
    )
  }
}

object StartDateController extends FormHelpers {

  val form: Form[LocalDate] = Form(
    single(
      "startDate" -> startDate
        .verifying("error.start-date.in-future", !_.isAfter(LocalDate.now))
        .verifying("error.start-date.before-tax-start", !_.isBefore(LocalDate.of(2018, 4, 6)))
    )
  )

  lazy val startDate: Mapping[LocalDate] = tuple(
    "day" -> numeric("day").verifying("error.start-day.invalid", d => d > 0 && d <= 31),
    "month" -> numeric("month").verifying("error.start-month.invalid", d => d > 0 && d <= 12),
    "year" -> numeric("year").verifying("error.start-year.invalid", d => d >= 1900 && d < 2100)
  ).verifying("error.date.invalid", x => x match {
    case (d, m, y) => Try(LocalDate.of(y, m, d)).isSuccess
  })
    .transform({ case (d, m, y) => LocalDate.of(y, m, d) }, d => (d.getDayOfMonth, d.getMonthValue, d.getYear))

  def numeric(key: String): Mapping[Int] = text
    .verifying(s"error.$key.required", _.nonEmpty)
    .verifying("error.number", v => v.isEmpty || Try(v.toInt).isSuccess)
    .transform[Int](_.toInt, _.toString)
}
