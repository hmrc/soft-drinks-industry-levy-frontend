/*
 * Copyright 2022 HM Revenue & Customs
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

import play.api.Configuration
import sdil.config.AppConfig

class TestConfig(override val configuration: Configuration) extends AppConfig(configuration) {
  override lazy val reportAProblemPartialUrl: String = "reportProblem"
  override lazy val reportAProblemNonJSUrl: String = "reportProblemNonJs"
  override lazy val ggLoginUrl: String = "http://localhost:9553/bas-gateway/sign-in"
  override lazy val sdilHomePage: String = "http://localhost:8700/soft-drinks-industry-levy/register/identify"
  override lazy val appName: String = "soft-drinks-industry-levy-frontend"
  override lazy val signoutRegVarUrl: String = "http://localhost:9553/bas-gateway/sign-out-without-state"
  override lazy val signoutReturnsUrl: String = "http://localhost:9553/bas-gateway/sign-out-without-state"
  override lazy val signoutUrlNoFeedback: String = "http://localhost:9553/bas-gateway/sign-out-without-state"
  override val balanceAllEnabled: Boolean = true
  override val directDebitEnabled: Boolean = true
}
