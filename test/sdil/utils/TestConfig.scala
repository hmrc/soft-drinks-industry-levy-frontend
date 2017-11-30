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

package sdil.utils

import java.time.LocalDate

import sdil.config.AppConfig

object TestConfig extends AppConfig {
  override val analyticsToken: String = "token"
  override val analyticsHost: String = "host"
  override val reportAProblemPartialUrl: String = "reportProblem"
  override val reportAProblemNonJSUrl: String = "reportProblemNonJs"
  override val betaFeedbackUrlAuth: String = "betaFeedback"
  override def taxStartDate: LocalDate = _taxStartDate
  override val ggLoginUrl: String = "/gg/sign-in"
  override val sdilHomePage: String = "sdilHome"
  override val appName: String = "appName"

  private var _taxStartDate: LocalDate = LocalDate.of(2018, 4, 6)

  def setTaxStartDate(date: LocalDate): Unit = {
    _taxStartDate = date
  }

  def resetTaxStartDate(): Unit = {
    _taxStartDate = LocalDate.of(2018, 4, 6)
  }
}
