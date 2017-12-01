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

import java.time.Clock

import com.kenshoo.play.metrics._
import controllers.Assets
import controllers.template.Template
import play.api.ApplicationLoader.Context
import play.api.http.{HttpErrorHandler, HttpRequestHandler}
import play.api.i18n.I18nComponents
import play.api.inject.{Injector, SimpleInjector}
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.{BuiltInComponentsFromContext, Configuration, DefaultApplication, Logger}
import play.filters.csrf.CSRFComponents
import play.filters.headers.SecurityHeadersComponents
import sdil.connectors.SoftDrinksIndustryLevyConnector
import sdil.controllers.{CopackedController, IdentifyController, ImportController, LitreageController, PackageCopackSmallController, ProductionSiteController, SDILController, StartDateController, VerifyController, WarehouseController}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.crypto.ApplicationCryptoDI
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.audit.DefaultAuditConnector
import uk.gov.hmrc.play.bootstrap.auth.DefaultAuthConnector
import uk.gov.hmrc.play.bootstrap.config.{Base64ConfigDecoder, ControllerConfigs}
import uk.gov.hmrc.play.bootstrap.filters._
import uk.gov.hmrc.play.bootstrap.filters.frontend._
import uk.gov.hmrc.play.bootstrap.filters.frontend.crypto.{CookieCryptoFilter, DefaultCookieCryptoFilter, SessionCookieCrypto}
import uk.gov.hmrc.play.bootstrap.filters.frontend.deviceid.{DefaultDeviceIdFilter, DeviceIdFilter}
import uk.gov.hmrc.play.bootstrap.http.{DefaultHttpClient, FrontendErrorHandler, HttpClient, RequestHandler}

class SDILComponents(context: Context)
  extends BuiltInComponentsFromContext(context)
    with Base64ConfigDecoder
    with I18nComponents
    with SecurityHeadersComponents
    with CSRFComponents
    with AhcWSComponents {

  override lazy val application = new DefaultApplication(
    environment,
    applicationLifecycle,
    customInjector,
    configuration,
    httpRequestHandler,
    errorHandler,
    actorSystem,
    materializer
  )

  override lazy val httpRequestHandler: HttpRequestHandler = new RequestHandler(router, errorHandler, httpConfiguration, filters)

  lazy val filters = new FrontendFilters(
    configuration,
    loggingFilter,
    headersFilter,
    securityHeadersFilter,
    frontendAuditFilter,
    metricsFilter,
    deviceIdFilter,
    csrfFilter,
    cookieCryptoFilter,
    sessionTimeoutFilter,
    cacheControlFilter
  )

  lazy val loggingFilter: LoggingFilter = new DefaultLoggingFilter(controllerConfigs)(materializer, actorSystem.dispatcher)
  lazy val headersFilter: HeadersFilter = new HeadersFilter(materializer)
  lazy val frontendAuditFilter: FrontendAuditFilter = new DefaultFrontendAuditFilter(configuration, controllerConfigs, auditConnector, materializer)
  lazy val metricsFilter: MetricsFilter = new MetricsFilterImpl(metrics)
  lazy val deviceIdFilter: DeviceIdFilter = new DefaultDeviceIdFilter(configuration, auditConnector)(materializer, actorSystem.dispatcher)
  lazy val cookieCryptoFilter: CookieCryptoFilter = new DefaultCookieCryptoFilter(sessionCookieCrypto)(materializer, actorSystem.dispatcher)
  lazy val sessionTimeoutFilter: SessionTimeoutFilter = new SessionTimeoutFilter(sessionTimeoutFilterConfig)(actorSystem.dispatcher, materializer)
  lazy val cacheControlFilter: CacheControlFilter = new CacheControlFilter(cacheControlConfig, materializer)(actorSystem.dispatcher)

  lazy val controllerConfigs: ControllerConfigs = ControllerConfigs.fromConfig(configuration)

  lazy val sessionCookieCrypto: SessionCookieCrypto = SessionCookieCrypto(new ApplicationCryptoDI(configuration).SessionCookieCrypto)

  lazy val sessionTimeoutFilterConfig: SessionTimeoutFilterConfig = SessionTimeoutFilterConfig.fromConfig(configuration)

  lazy val cacheControlConfig: CacheControlConfig = CacheControlConfig.fromConfig(configuration)

  override lazy val configuration: Configuration = decodeConfig(context.initialConfiguration)

  lazy val appConfig: AppConfig = new FrontendAppConfig(configuration, environment)

  lazy val errorHandler: FrontendErrorHandler = new SDILErrorHandler(messagesApi, configuration)(appConfig)
  override lazy val httpErrorHandler: HttpErrorHandler = errorHandler

  lazy val auditConnector: AuditConnector = new DefaultAuditConnector(configuration, environment)

  lazy val httpClient: HttpClient = new DefaultHttpClient(configuration, auditConnector)

  lazy val authConnector: AuthConnector = new DefaultAuthConnector(httpClient, configuration, environment)
  lazy val cache: SessionCache = new FormDataCache(httpClient, configuration, environment)
  lazy val sdilConnector: SoftDrinksIndustryLevyConnector = new SoftDrinksIndustryLevyConnector(httpClient, environment, configuration)(actorSystem.dispatcher)

  lazy val assets: Assets = new Assets(httpErrorHandler)
  lazy val sdilController: SDILController = new SDILController(messagesApi, authConnector, cache, sdilConnector)(appConfig)
  lazy val identifyController: IdentifyController = new IdentifyController(messagesApi, cache)(appConfig)
  lazy val verifyController: VerifyController = new VerifyController(messagesApi, cache)(appConfig)
  lazy val litreageController: LitreageController = new LitreageController(messagesApi, errorHandler, cache)(appConfig)
  lazy val packageCopackController: PackageCopackSmallController = new PackageCopackSmallController(messagesApi, cache)(appConfig)
  lazy val copackedController: CopackedController = new CopackedController(messagesApi, cache)(appConfig)
  lazy val importController: ImportController = new ImportController(messagesApi, cache)(appConfig)
  lazy val startDateController: StartDateController = new StartDateController(messagesApi, Clock.systemDefaultZone(), cache)(appConfig)
  lazy val productionSiteController: ProductionSiteController = new ProductionSiteController(messagesApi, Clock.systemDefaultZone(), cache)(appConfig)
  lazy val warehouseController: WarehouseController = new WarehouseController(messagesApi, cache)(appConfig)

  lazy val appRoutes: app.Routes = new app.Routes(
    httpErrorHandler,
    assets,
    sdilController,
    identifyController,
    verifyController,
    litreageController,
    packageCopackController,
    copackedController,
    importController,
    startDateController,
    productionSiteController,
    warehouseController
  )

  lazy val healthRoutes: health.Routes = new health.Routes()

  lazy val templateController: Template = new Template(httpErrorHandler)

  lazy val customInjector: Injector = new SimpleInjector(injector) + templateController + wsApi

  lazy val templateRoutes: template.Routes = new template.Routes()

  lazy val metrics: Metrics = new MetricsImpl(applicationLifecycle, configuration)

  lazy val metricsController: MetricsController = new MetricsController(metrics)

  override def router: prod.Routes = new prod.Routes(httpErrorHandler, appRoutes, healthRoutes, templateRoutes, metricsController, "")
}
