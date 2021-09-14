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

import com.kenshoo.play.metrics.{Metrics, MetricsImpl}
import com.softwaremill.macwire._
import controllers.template.Template
import controllers.{AssetsConfiguration, AssetsMetadata, DefaultAssetsMetadata}
import play.api.ApplicationLoader.Context
import play.api.http.HttpErrorHandler
import play.api.i18n.I18nComponents
import play.api.inject.{ApplicationLifecycle, DefaultApplicationLifecycle, Injector, SimpleInjector}
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.mvc.{DefaultMessagesControllerComponents, _}
import play.api.{BuiltInComponentsFromContext, Configuration, DefaultApplication}
import play.filters.csrf.CSRFComponents
import play.filters.headers.SecurityHeadersComponents
import sdil.filters.SdilFilters
import uk.gov.hmrc.play.bootstrap.config.Base64ConfigDecoder
import uk.gov.hmrc.play.config.{AccessibilityStatementConfig, AssetsConfig, GTMConfig, OptimizelyConfig}

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class SDILComponents @Inject()(context: Context)(
  sdilFilters: SdilFilters,
  template: Template,
  errorHandler: SDILErrorHandler,
  defaultApplication: DefaultApplication,
  metadata: DefaultAssetsMetadata,
  messagesControllerComponents: DefaultMessagesControllerComponents,
  metricsImpl: MetricsImpl)
    extends BuiltInComponentsFromContext(context) with Base64ConfigDecoder with I18nComponents
    with SecurityHeadersComponents with CSRFComponents with AhcWSComponents {

  override lazy val httpFilters = sdilFilters.filters
  override lazy val application: DefaultApplication = defaultApplication
  override lazy val applicationLifecycle: ApplicationLifecycle = wire[DefaultApplicationLifecycle]

  implicit lazy val ec: ExecutionContext = actorSystem.dispatcher

  override lazy val configuration: Configuration = decodeConfig(context.initialConfiguration)

  override lazy val httpErrorHandler: HttpErrorHandler = errorHandler

  lazy val templateController: Template = template

  lazy val optimizelyConfig: OptimizelyConfig = new OptimizelyConfig(configuration)
  lazy val assetConfig: AssetsConfig = new AssetsConfig(configuration)
  lazy val gtmConfig: GTMConfig = new GTMConfig(configuration)
  lazy val accessibilityStatementConfig: AccessibilityStatementConfig = new AccessibilityStatementConfig(
    (configuration))

  lazy val customInjector
    : Injector = new SimpleInjector(injector) + templateController + wsClient + optimizelyConfig + assetConfig + gtmConfig

  lazy val messagesActionBuilder = new DefaultMessagesActionBuilderImpl(
    controllerComponents.parsers.defaultBodyParser,
    controllerComponents.messagesApi)
  override val mcc: MessagesControllerComponents = messagesControllerComponents
  override val assetsMetadata: AssetsMetadata = metadata
  lazy val assetsConfiguration = new AssetsConfiguration()
  override val appName = configuration.get[String]("appName")

  override lazy val metrics: Metrics = metricsImpl
}
