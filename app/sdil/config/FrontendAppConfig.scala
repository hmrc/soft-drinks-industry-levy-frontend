/*
 * Copyright 2019 HM Revenue & Customs
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

import play.api.{Configuration, Environment}
import uk.gov.hmrc.play.bootstrap.config.{RunMode, ServicesConfig}

trait AppConfig {
  val analyticsToken: String
  val analyticsHost: String
  val reportAProblemPartialUrl: String
  val reportAProblemNonJSUrl: String
  val ggLoginUrl: String
  val signoutRegVarUrl: String
  val signoutReturnsUrl: String
  val signoutUrlNoFeedback: String
  val sdilHomePage: String
  val appName: String
  val balanceAllEnabled: Boolean
}

class FrontendAppConfig(val runModeConfiguration: Configuration, runMode: RunMode, environment: Environment)
    extends ServicesConfig(runModeConfiguration, runMode) with AppConfig {

  private def loadConfig(key: String) =
    runModeConfiguration.getString(key).getOrElse(throw new Exception(s"Missing configuration key: $key"))

  private lazy val contactHost = runModeConfiguration.getString(s"contact-frontend.host").getOrElse("")
  private lazy val contactFormServiceIdentifier = runModeConfiguration.getString("appName").get

  override lazy val analyticsToken: String = loadConfig(s"google-analytics.token")
  override lazy val analyticsHost: String = loadConfig(s"google-analytics.host")
  override lazy val reportAProblemPartialUrl =
    s"$contactHost/contact/problem_reports_ajax?service=$contactFormServiceIdentifier"
  override lazy val reportAProblemNonJSUrl =
    s"$contactHost/contact/problem_reports_nonjs?service=$contactFormServiceIdentifier"

  //Auth related config
  lazy val appName: String = loadConfig("appName")
  private lazy val companyAuthFrontend = getConfString("company-auth.url", "")
  private lazy val companyAuthSignInPath = getConfString("company-auth.sign-in-path", "")
  private lazy val companyAuthSignOutPath = getConfString("company-auth.sign-out-path", "")
  lazy val ggLoginUrl: String = s"$companyAuthFrontend$companyAuthSignInPath"
  lazy val feedbackSurveyUrl: String = loadConfig("microservice.services.feedback-survey.url")
  lazy val signOutSdilUrl: String = s"$companyAuthFrontend$companyAuthSignOutPath?continue=$feedbackSurveyUrl"
  lazy val signoutRegVarUrl: String = s"$signOutSdilUrl?origin=SDIL"
  lazy val signoutReturnsUrl: String = s"$signOutSdilUrl?origin=SDILRETURN"
  lazy val signoutUrlNoFeedback: String = s"$companyAuthFrontend$companyAuthSignOutPath"
  lazy val sdilHomePage: String = loadConfig("sdil-home-page-url")

  lazy val payApiBaseUrl: String = loadConfig("payments.payApiBaseUrl")
  override val balanceAllEnabled: Boolean = getBoolean("balanceAll.enabled")
}
