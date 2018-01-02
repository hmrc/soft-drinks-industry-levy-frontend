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

package sdil.config

import com.kenshoo.play.metrics.{Metrics, MetricsFilter, MetricsFilterImpl}
import com.softwaremill.macwire.{wire, wireWith}
import play.api.http.HttpConfiguration
import play.filters.csrf.CSRFFilter
import play.filters.headers.SecurityHeadersFilter
import uk.gov.hmrc.crypto.ApplicationCryptoDI
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.config.ControllerConfigs
import uk.gov.hmrc.play.bootstrap.filters._
import uk.gov.hmrc.play.bootstrap.filters.frontend._
import uk.gov.hmrc.play.bootstrap.filters.frontend.crypto.{CookieCryptoFilter, DefaultCookieCryptoFilter, SessionCookieCrypto}
import uk.gov.hmrc.play.bootstrap.filters.frontend.deviceid.{DefaultDeviceIdFilter, DeviceIdFilter}

trait FilterWiring extends CommonWiring {
  val httpConfiguration: HttpConfiguration
  val securityHeadersFilter: SecurityHeadersFilter
  val csrfFilter: CSRFFilter
  val auditConnector: AuditConnector
  val metrics: Metrics

  lazy val filters: FrontendFilters = wire[FrontendFilters]

  lazy val loggingFilter: LoggingFilter = wire[DefaultLoggingFilter]
  lazy val headersFilter: HeadersFilter = wire[HeadersFilter]
  lazy val frontendAuditFilter: FrontendAuditFilter = wire[DefaultFrontendAuditFilter]
  lazy val metricsFilter: MetricsFilter = wire[MetricsFilterImpl]
  lazy val deviceIdFilter: DeviceIdFilter = wire[DefaultDeviceIdFilter]
  lazy val cookieCryptoFilter: CookieCryptoFilter = wire[DefaultCookieCryptoFilter]
  lazy val sessionTimeoutFilter: SessionTimeoutFilter = wire[SessionTimeoutFilter]
  lazy val cacheControlFilter: CacheControlFilter = wire[CacheControlFilter]

  lazy val controllerConfigs: ControllerConfigs = wireWith(ControllerConfigs.fromConfig _)

  lazy val sessionCookieCrypto: SessionCookieCrypto = SessionCookieCrypto(new ApplicationCryptoDI(configuration).SessionCookieCrypto)

  lazy val sessionTimeoutFilterConfig: SessionTimeoutFilterConfig = wireWith(SessionTimeoutFilterConfig.fromConfig _)

  lazy val cacheControlConfig: CacheControlConfig = wireWith(CacheControlConfig.fromConfig _)
}
