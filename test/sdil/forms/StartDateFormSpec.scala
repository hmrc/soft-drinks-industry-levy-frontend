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

package sdil.forms

import java.time.LocalDate

import sdil.config.AppConfig
import sdil.controllers.StartDateController.form
import sdil.utils.TestConfig

class StartDateFormSpec extends FormSpec {

  "The start date form" should {
    "require the day" in {
      mustRequire(keys.day)(form, validData, "error.day.required")
    }

    "require the month" in {
      mustRequire(keys.month)(form, validData, "error.month.required")
    }

    "require the year" in {
      mustRequire(keys.year)(form, validData, "error.year.required")
    }

    "require the day to be valid" in {
      Seq("0", "32", "-1") foreach { invalid =>
        val f = form.bind(validData.updated(keys.day, invalid))
        mustContainError(f, keys.day, "error.start-day.invalid")
      }
    }

    "require the month to be valid" in {
      Seq("0", "13", "-1") foreach { invalid =>
        val f = form.bind(validData.updated(keys.month, invalid))
        mustContainError(f, keys.month, "error.start-month.invalid")
      }
    }

    "require the year to be between 1900 and 2100" in {
      Seq("0", "1899", "2101") foreach { invalid =>
        val f = form.bind(validData.updated(keys.year, invalid))
        mustContainError(f, keys.year, "error.start-year.invalid")
      }
    }

    "require the date to be valid" in {
      Seq(("29", "2", "2017"), ("31", "9", "2017")) foreach { case (d, m, y) =>
        val f = form.bind(Map(keys.day -> d, keys.month -> m, keys.year -> y))
        mustContainError(f, "startDate", "error.date.invalid")
      }
    }

    "require the date to be in the past" in {
      val tomorrow = LocalDate.now plusDays 1
      val f = form.bind(Map(
        keys.day -> tomorrow.getDayOfMonth.toString,
        keys.month -> tomorrow.getMonthValue.toString,
        keys.year -> tomorrow.getYear.toString
      ))

      mustContainError(f, "startDate", "error.start-date.in-future")
    }
  }

  lazy val keys = new {
    val day = "startDate.day"
    val month = "startDate.month"
    val year = "startDate.year"
  }

  lazy val validData = Map(
    keys.day -> LocalDate.now.getDayOfMonth.toString,
    keys.month -> LocalDate.now.getMonthValue.toString,
    keys.year -> LocalDate.now.getYear.toString
  )

  lazy implicit val config: AppConfig = {
    val c = new TestConfig
    c.setTaxStartDate(LocalDate.of(2017, 1, 1))
    c
  }

}
