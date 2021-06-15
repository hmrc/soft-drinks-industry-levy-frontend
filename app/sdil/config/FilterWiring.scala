/*
 * Copyright 2021 HM Revenue & Customs
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

import java.util.UUID

import com.kenshoo.play.metrics.{Metrics, MetricsFilter, MetricsFilterImpl}
import com.softwaremill.macwire.{wire, wireWith}
import play.api.http.HttpConfiguration
import play.api.libs.crypto.DefaultCookieSigner
import play.api.mvc.DefaultSessionCookieBaker
import play.filters.csrf.CSRFFilter
import play.filters.headers.SecurityHeadersFilter
import uk.gov.hmrc.crypto.ApplicationCrypto
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.config.{ControllerConfigs, DefaultHttpAuditEvent, HttpAuditEvent}
import uk.gov.hmrc.play.bootstrap.filters._
import uk.gov.hmrc.play.bootstrap.frontend.filters._
import uk.gov.hmrc.play.bootstrap.frontend.filters.crypto.{DefaultSessionCookieCryptoFilter, SessionCookieCrypto, SessionCookieCryptoFilter}
import uk.gov.hmrc.play.bootstrap.frontend.filters.deviceid.{DefaultDeviceIdFilter, DeviceIdFilter}
import uk.gov.hmrc.play.bootstrap.frontend.http.FrontendErrorHandler

trait FilterWiring extends CommonWiring {
  val httpConfiguration: HttpConfiguration
  val securityHeadersFilter: SecurityHeadersFilter
  val csrfFilter: CSRFFilter
  val auditConnector: AuditConnector
  val metrics: Metrics
  val errorHandler: FrontendErrorHandler

  lazy val sessionCookieBaker: DefaultSessionCookieBaker =
    new DefaultSessionCookieBaker(
      httpConfiguration.session,
      httpConfiguration.secret,
      new DefaultCookieSigner(httpConfiguration.secret))
  val uuid: UUID = UUID.randomUUID()
  lazy val sessionIdFilter: SessionIdFilter = wire[SessionIdFilter]
  lazy val mdcFilter: MDCFilter = wire[MDCFilter]
  lazy val loggingFilter: LoggingFilter = wire[DefaultLoggingFilter]
  lazy val headersFilter: HeadersFilter = wire[HeadersFilter]
  lazy val frontendAuditFilter: FrontendAuditFilter = wire[DefaultFrontendAuditFilter]
  lazy val metricsFilter: MetricsFilter = wire[MetricsFilterImpl]
  lazy val deviceIdFilter: DeviceIdFilter = wire[DefaultDeviceIdFilter]
  lazy val cookieCryptoFilter: SessionCookieCryptoFilter = wire[DefaultSessionCookieCryptoFilter]
  lazy val sessionTimeoutFilter: SessionTimeoutFilter = wire[SessionTimeoutFilter]
  lazy val cacheControlFilter: CacheControlFilter = wire[CacheControlFilter]
  lazy val controllerConfigs: ControllerConfigs = wireWith(ControllerConfigs.fromConfig _)
  lazy val sessionCookieCrypto: SessionCookieCrypto = SessionCookieCrypto(
    new ApplicationCrypto(configuration.underlying).SessionCookieCrypto)
  lazy val sessionTimeoutFilterConfig: SessionTimeoutFilterConfig = wireWith(SessionTimeoutFilterConfig.fromConfig _)
  lazy val cacheControlConfig: CacheControlConfig = wireWith(CacheControlConfig.fromConfig _)
  lazy val httpAuditEvent: HttpAuditEvent = wire[DefaultHttpAuditEvent]
  val appName: String

  val applicationCrypto = new ApplicationCrypto(configuration.underlying)
}
