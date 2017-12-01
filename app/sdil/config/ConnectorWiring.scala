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

import com.softwaremill.macwire.wire
import sdil.connectors.SoftDrinksIndustryLevyConnector
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.audit.DefaultAuditConnector
import uk.gov.hmrc.play.bootstrap.auth.DefaultAuthConnector
import uk.gov.hmrc.play.bootstrap.http.{DefaultHttpClient, HttpClient}

trait ConnectorWiring extends CommonWiring {
  lazy val auditConnector: AuditConnector = wire[DefaultAuditConnector]
  lazy val httpClient: HttpClient = wire[DefaultHttpClient]
  lazy val authConnector: AuthConnector = wire[DefaultAuthConnector]
  lazy val sdilConnector: SoftDrinksIndustryLevyConnector = wire[SoftDrinksIndustryLevyConnector]
}
