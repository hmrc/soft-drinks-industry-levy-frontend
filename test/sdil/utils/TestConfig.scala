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

package sdil.utils

import java.time.LocalDate

import sdil.config.AppConfig

class TestConfig extends AppConfig {
  override val analyticsToken: String = "token"
  override val analyticsHost: String = "host"
  override val reportAProblemPartialUrl: String = "reportProblem"
  override val reportAProblemNonJSUrl: String = "reportProblemNonJs"
  override val betaFeedbackUrlAuth: String = "betaFeedback"
  override def taxStartDate: LocalDate = _taxStartDate
  override val ggLoginUrl: String = "http://localhost:9025/gg/sign-in"
  override val sdilHomePage: String = "http://localhost:8700/soft-drinks-industry-levy/register/identify"
  override val appName: String = "soft-drinks-industry-levy-frontend"

  private var _taxStartDate: LocalDate = LocalDate.of(2018, 4, 6)

  def setTaxStartDate(date: LocalDate): Unit = {
    _taxStartDate = date
  }

  def resetTaxStartDate(): Unit = {
    _taxStartDate = LocalDate.of(2018, 4, 6)
  }

  private var _whitelist: Seq[String] = Nil

  def enableWhitelist(utrs: String*): Unit = {
    _whitelist = utrs
  }

  def disableWhitelist(): Unit = {
    _whitelist = Nil
  }

  override def isWhitelisted(utr: String): Boolean = _whitelist.contains(utr)

  override def whitelistEnabled: Boolean = _whitelist.nonEmpty

  override val signoutUrl: String = "http://localhost:9025/gg/sign-out"

  override val variationsEnabled = true
}
