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

import akka.actor.ActorSystem
import com.softwaremill.macwire.wire
import play.api.inject.{ApplicationLifecycle, DefaultApplicationLifecycle}
import play.api.libs.ws.WSClient
import sdil.connectors.{DirectDebitBackendConnector, GaConnector, PayApiConnector, SoftDrinksIndustryLevyConnector, TestConnector}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.HttpClient
import uk.gov.hmrc.play.audit.http.HttpAuditing
import uk.gov.hmrc.play.audit.http.config.AuditingConfig
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.audit.{DefaultAuditChannel, DefaultAuditConnector} //, DefaultAuditCounter}
import uk.gov.hmrc.play.bootstrap.auth.DefaultAuthConnector
import uk.gov.hmrc.play.bootstrap.config.{AuditingConfigProvider, ServicesConfig}
import uk.gov.hmrc.play.bootstrap.http.{DefaultHttpAuditing, DefaultHttpClient}
import uk.gov.hmrc.play.audit.http.connector.AuditChannel
//import uk.gov.hmrc.play.audit.http.connector.AuditCounter

trait ConnectorWiring extends CommonWiring {
  val wsClient: WSClient
  val actorSystem: ActorSystem
  lazy val applicationLifecycle: ApplicationLifecycle = wire[DefaultApplicationLifecycle]
  //lazy val auditCounter: AuditCounter = wire[DefaultAuditCounter]
  lazy val auditChannel: AuditChannel = wire[DefaultAuditChannel]
  lazy val auditConnector: AuditConnector = wire[DefaultAuditConnector]
  lazy val httpClient: HttpClient = wire[DefaultHttpClient]
  lazy val serviceconfig: ServicesConfig = wire[ServicesConfig]
  lazy val authConnector: AuthConnector = new DefaultAuthConnector(httpClient, serviceconfig)
  lazy val sdilConnector: SoftDrinksIndustryLevyConnector = wire[SoftDrinksIndustryLevyConnector]
  lazy val payApiConnector: PayApiConnector = new PayApiConnector(httpClient, serviceconfig)
  lazy val directDebitBackendConnector: DirectDebitBackendConnector =
    new DirectDebitBackendConnector(httpClient, serviceconfig)
  lazy val testConnector: TestConnector = wire[TestConnector]
  lazy val gaConnector: GaConnector = wire[GaConnector]
  lazy val httpAuditing: HttpAuditing = wire[DefaultHttpAuditing]
  lazy val auditingConfigProvider: AuditingConfigProvider = wire[AuditingConfigProvider]
  lazy val auditingConfig: AuditingConfig = auditingConfigProvider.get()
  val appName: String
}
