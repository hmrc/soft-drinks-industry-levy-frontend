/*
 * Copyright 2024 HM Revenue & Customs
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

import play.api.Configuration
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.Inject

class AppConfig @Inject()(val configuration: Configuration) extends ServicesConfig(configuration) {

  private def loadConfig(key: String) =
    configuration.getOptional[String](key).getOrElse(throw new Exception(s"Missing configuration key: $key"))

  private lazy val contactHost = configuration.getOptional[String](s"contact-frontend.host").getOrElse("")
  private lazy val contactFormServiceIdentifier = configuration.get[String]("appName")

  lazy val reportAProblemNonJSUrl =
    s"$contactHost/contact/report-technical-problem?service=$contactFormServiceIdentifier"

  //Auth related config
  lazy val appName: String = loadConfig("appName")
  private lazy val basGatewayFrontend = getConfString("bas-gateway.url", "")
  private lazy val basGatewaySignInPath = getConfString("bas-gateway.sign-in-path", "")
  private lazy val basGatewaySignOutPath = getConfString("bas-gateway.sign-out-path", "")
  lazy val ggLoginUrl: String = s"$basGatewayFrontend$basGatewaySignInPath"
  lazy val feedbackSurveyUrl: String = loadConfig("microservice.services.feedback-survey.url")
  lazy val signOutSdilUrl: String = s"$basGatewayFrontend$basGatewaySignOutPath?continue=$feedbackSurveyUrl"
  lazy val signoutRegVarUrl: String = s"$signOutSdilUrl/SDIL"
  lazy val signoutReturnsUrl: String = s"$signOutSdilUrl/SDILRETURN"
  lazy val signoutUrlNoFeedback: String = s"$basGatewayFrontend$basGatewaySignOutPath"
  lazy val sdilHomePage: String = loadConfig("sdil-home-page-url")
  lazy val sdilNewRegistrationUrl: String = loadConfig("sdilNewRegistrationUrl")
  val redirectToNewRegistrationsEnabled: Boolean = getBoolean("redirectToNewRegistration.enabled")
  val redirectToNewReturnsEnabled: Boolean = getBoolean("redirectToNewReturns.enabled")
  val redirectToNewServiceEnabled: Boolean = getBoolean("redirectToNewService.enabled")
  lazy val sdilNewHomeUrl: String = loadConfig("sdilNewHomeUrl")

  val returnsBaseUrl = loadConfig("sdilNewReturnsFrontend")
  def startReturnUrl(year: Int, quarter: Int, isNilReturn: Boolean) =
    s"$returnsBaseUrl/submit-return/year/$year/quarter/$quarter/nil-return/$isNilReturn"

  val balanceAllEnabled: Boolean = getBoolean("balanceAll.enabled")
  val directDebitEnabled: Boolean = getBoolean("directDebit.enabled")

}
