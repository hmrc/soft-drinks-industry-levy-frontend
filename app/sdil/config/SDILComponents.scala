/*
 * Copyright 2019 HM Revenue & Customs
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

import com.softwaremill.macwire._
import controllers.template.Template
import play.api.ApplicationLoader.Context
import play.api.http.{HttpErrorHandler, HttpRequestHandler}
import play.api.i18n.I18nComponents
import play.api.inject.{Injector, SimpleInjector}
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.{BuiltInComponentsFromContext, Configuration, DefaultApplication}
import play.filters.csrf.CSRFComponents
import play.filters.headers.SecurityHeadersComponents
import uk.gov.hmrc.play.bootstrap.config.Base64ConfigDecoder
import uk.gov.hmrc.play.bootstrap.http.RequestHandler
import uk.gov.hmrc.play.health.HealthController

import scala.concurrent.ExecutionContext

class SDILComponents(context: Context)
  extends BuiltInComponentsFromContext(context)
    with Base64ConfigDecoder
    with I18nComponents
    with SecurityHeadersComponents
    with CSRFComponents
    with AhcWSComponents
    with RoutesWiring
    with FilterWiring
    with ConnectorWiring
    with ConfigWiring {

  override lazy val application: DefaultApplication = wire[DefaultApplication]

  implicit lazy val ec: ExecutionContext = actorSystem.dispatcher

  override lazy val configuration: Configuration = decodeConfig(context.initialConfiguration)

  override lazy val httpRequestHandler: HttpRequestHandler = wire[RequestHandler]
  override lazy val httpErrorHandler: HttpErrorHandler = errorHandler

  lazy val adminController: HealthController = wire[HealthController]
  lazy val templateController: Template = wire[Template]

  lazy val customInjector: Injector = new SimpleInjector(injector) + templateController + adminController + wsApi
}