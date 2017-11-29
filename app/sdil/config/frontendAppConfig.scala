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

package sdil.config

import java.time.LocalDate

import play.api.Play.{configuration, current}
import uk.gov.hmrc.play.config.ServicesConfig

trait AppConfig {
  val analyticsToken: String
  val analyticsHost: String
  val reportAProblemPartialUrl: String
  val reportAProblemNonJSUrl: String
  val betaFeedbackUrlAuth: String
}

object FrontendAppConfig extends AppConfig with ServicesConfig {

  private def loadConfig(key: String) = configuration.getString(key).getOrElse(throw new Exception(s"Missing configuration key: $key"))

  private val contactHost = configuration.getString(s"contact-frontend.host").getOrElse("")
  private val contactFormServiceIdentifier = configuration.getString("appName").get

  override lazy val analyticsToken = loadConfig(s"google-analytics.token")
  override lazy val analyticsHost = loadConfig(s"google-analytics.host")
  override lazy val reportAProblemPartialUrl = s"$contactHost/contact/problem_reports_ajax?service=$contactFormServiceIdentifier"
  override lazy val reportAProblemNonJSUrl = s"$contactHost/contact/problem_reports_nonjs?service=$contactFormServiceIdentifier"
  override lazy val betaFeedbackUrlAuth = s"$contactHost/contact/beta-feedback?service=$contactFormServiceIdentifier"

  //Auth related config
  val appName = loadConfig("appName")
  private lazy val companyAuthFrontend = getConfString("company-auth.url", "")
  private lazy val companyAuthSignInPath = getConfString("company-auth.sign-in-path", "")
  private lazy val companyAuthSignOutPath = getConfString("company-auth.sign-out-path", "")
  val ggLoginUrl: String = s"$companyAuthFrontend$companyAuthSignInPath"
  val sdilHomePage = loadConfig("sdil-home-page-url")
  val taxStartDate = LocalDate.parse(loadConfig("tax-start-date"))
}
