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

package sdil.config

import akka.stream.Materializer
import com.softwaremill.macwire.wire
import play.api.i18n.MessagesApi
import play.api.{Configuration, Environment, Mode}
import uk.gov.hmrc.http.cache.client.ShortLivedHttpCaching
import uk.gov.hmrc.http.HttpClient
import uk.gov.hmrc.play.config._
import uk.gov.hmrc.play.views.html.helpers._
import uk.gov.hmrc.play.views.html.layouts._
import views.{ViewHelpers, Views}
import views.html.softdrinksindustrylevy.{balance_history, deregistered_service_page, service_page}
import views.html.softdrinksindustrylevy.errors.{already_registered, invalid_affinity, invalid_role, registration_pending}
import views.html.{error_template, main_template, time_out}
import views.softdrinksindustrylevy.errors.Errors
import views.uniform.Uniform

import scala.concurrent.ExecutionContext

trait CommonWiring {
  val configuration: Configuration
  val environment: Environment
  val messagesApi: MessagesApi
  val httpClient: HttpClient
  implicit val ec: ExecutionContext
  implicit val appConfig: AppConfig
  implicit val materializer: Materializer
  lazy val mode: Mode = environment.mode
  lazy val shortLivedCaching: ShortLivedHttpCaching = wire[SDILShortLivedCaching]
  lazy val keystore: SDILSessionCache = wire[SDILSessionCache]

  lazy val errors: Errors = wire[Errors]
  lazy val Views: Views = wire[Views]
  lazy val uniformHelpers: Uniform = wire[Uniform]

  lazy val alreadyRegistered: already_registered = wire[already_registered]
  lazy val invalidAffinity: invalid_affinity = wire[invalid_affinity]
  lazy val invalidRole: invalid_role = wire[invalid_role]
  lazy val registrationPending: registration_pending = wire[registration_pending]
  lazy val timeOut: time_out = wire[time_out]
  lazy val identify: views.html.softdrinksindustrylevy.register.identify =
    wire[views.html.softdrinksindustrylevy.register.identify]
  lazy val verify: views.html.softdrinksindustrylevy.register.verify =
    wire[views.html.softdrinksindustrylevy.register.verify]
  lazy val balanceHistory: balance_history = wire[balance_history]
  lazy val deregisteredServicePage: deregistered_service_page = wire[deregistered_service_page]
  lazy val servicePage: service_page = wire[service_page]
  lazy val errorTemplate: error_template = wire[error_template]

  lazy val updateBusinessAddresses: views.html.uniform.fragments.update_business_addresses =
    wire[views.html.uniform.fragments.update_business_addresses]
  // lazy val ask: views.html.uniform.ask = wire[views.html.uniform.ask]
  // lazy val cya: views.html.uniform.cya = wire[views.html.uniform.cya]
  // lazy val end: views.html.uniform.end = wire[views.html.uniform.end]
  // lazy val journeyEnd: views.html.uniform.journeyEnd = wire[views.html.uniform.journeyEnd]
  // lazy val many: views.html.uniform.many = wire[views.html.uniform.many]
  // lazy val tell: views.html.uniform.tell = wire[views.html.uniform.tell]

  // UF5 changes
  lazy val base: views.html.uniform.base = wire[views.html.uniform.base]

  val optimizelyConfig: OptimizelyConfig
}
