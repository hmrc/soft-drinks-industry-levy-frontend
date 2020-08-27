/*
 * Copyright 2020 HM Revenue & Customs
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

package sdil.filters

import com.kenshoo.play.metrics.MetricsFilter
import play.api.Configuration
import play.api.mvc.EssentialFilter
import play.filters.csrf.CSRFFilter
import play.filters.headers.SecurityHeadersFilter
import uk.gov.hmrc.play.bootstrap.filters.{CacheControlFilter, LoggingFilter, MDCFilter}
import uk.gov.hmrc.play.bootstrap.frontend.filters.crypto.SessionCookieCryptoFilter
import uk.gov.hmrc.play.bootstrap.frontend.filters.deviceid.DeviceIdFilter
import uk.gov.hmrc.play.bootstrap.frontend.filters.{FrontendAuditFilter, FrontendFilters, HeadersFilter, SessionIdFilter, SessionTimeoutFilter, WhitelistFilter}

class SdilFilters(
  configuration: Configuration,
  loggingFilter: LoggingFilter,
  headersFilter: HeadersFilter,
  securityFilter: SecurityHeadersFilter,
  frontendAuditFilter: FrontendAuditFilter,
  metricsFilter: MetricsFilter,
  deviceIdFilter: DeviceIdFilter,
  csrfFilter: CSRFFilter,
  cookieCryptoFilter: SessionCookieCryptoFilter,
  sessionTimeoutFilter: SessionTimeoutFilter,
  cacheControlFilter: CacheControlFilter,
  mdcFilter: MDCFilter,
  whitelistFilter: WhitelistFilter,
  sessionIdFilter: SessionIdFilter,
  variationsFilter: VariationsFilter)
    extends FrontendFilters(
      configuration: Configuration,
      loggingFilter: LoggingFilter,
      headersFilter: HeadersFilter,
      securityFilter: SecurityHeadersFilter,
      frontendAuditFilter: FrontendAuditFilter,
      metricsFilter: MetricsFilter,
      deviceIdFilter: DeviceIdFilter,
      csrfFilter: CSRFFilter,
      cookieCryptoFilter: SessionCookieCryptoFilter,
      sessionTimeoutFilter: SessionTimeoutFilter,
      cacheControlFilter: CacheControlFilter,
      mdcFilter: MDCFilter,
      whitelistFilter: WhitelistFilter,
      sessionIdFilter: SessionIdFilter
    ) {}
