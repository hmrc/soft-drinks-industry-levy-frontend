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

package sdil.config

import com.kenshoo.play.metrics.{Metrics, MetricsController}
import com.softwaremill.macwire.wire
import controllers.template.Template
import controllers.{Assets, AssetsMetadata}
import play.api.i18n.MessagesApi
import play.api.mvc.MessagesControllerComponents
import play.api.routing.Router
import sdil.actions.{AuthorisedAction, FormAction, RegisteredAction}
import sdil.connectors._
import sdil.controllers.test.{TestController, TestingController}
import sdil.controllers.{VariationsController, RegistrationController => UniformRegistrationController, _}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.cache.client.{SessionCache, ShortLivedHttpCaching}
import uk.gov.hmrc.play.bootstrap.http.{FrontendErrorHandler, HttpClient}
import uk.gov.hmrc.play.health.HealthController
import views.{ViewHelpers, Views}
import views.html.{error_template, govuk_wrapper, main_template, time_out}
import views.html.softdrinksindustrylevy.{balance_history, deregistered_service_page, service_page}
import views.html.softdrinksindustrylevy.errors.{already_registered, invalid_affinity, invalid_role, registration_pending}
import views.softdrinksindustrylevy.errors.Errors
import views.uniform.Uniform
import uk.gov.hmrc.play.views.html.helpers._
import uk.gov.hmrc.play.views.html.layouts._

trait RoutesWiring extends CommonWiring {
  val errorHandler: FrontendErrorHandler
  val httpClient: HttpClient
  val authConnector: AuthConnector
  val cache: RegistrationFormDataCache
  val shortLivedCaching: ShortLivedHttpCaching
  val sdilConnector: SoftDrinksIndustryLevyConnector
  val payApiConnector: PayApiConnector
  val directDebitBackendConnector: DirectDebitBackendConnector
  val testConnector: TestConnector
  val gaConnector: GaConnector
  val keystore: SessionCache
  val messagesApi: MessagesApi

  lazy val authorisedAction: AuthorisedAction = wire[AuthorisedAction]
  lazy val formAction: FormAction = wire[FormAction]
  lazy val registeredAction: RegisteredAction = wire[RegisteredAction]
  lazy val assets: Assets = wire[Assets]
  lazy val servicePageController: ServicePageController = wire[ServicePageController]
  lazy val paymentController: PaymentController = wire[PaymentController]
  lazy val directDebitController: DirectDebitController = wire[DirectDebitController]
  lazy val identifyController: IdentifyController = wire[IdentifyController]
  lazy val verifyController: VerifyController = wire[VerifyController]
  lazy val signoutController: AuthenticationController = wire[AuthenticationController]
  lazy val testController: TestingController = wire[TestingController]
  lazy val newTestController: TestController = wire[TestController]

  lazy val VariationsController: VariationsController = wire[VariationsController]
  lazy val returnsController: ReturnsController = wire[ReturnsController]
  lazy val uniformRegistrationsController: UniformRegistrationController = wire[UniformRegistrationController]

  private lazy val appRoutes: app.Routes = wire[app.Routes]
  private lazy val healthRoutes = wire[health.Routes]
  private lazy val templateRoutes = wire[template.Routes]
  private lazy val prodRoutes: prod.Routes = wire[prod.Routes]

  private lazy val testOnlyRoutes: testOnlyDoNotUseInAppConf.Routes = wire[testOnlyDoNotUseInAppConf.Routes]

  val metrics: Metrics
  lazy val metricsController: MetricsController = wire[MetricsController]

  lazy val prefix: String = ""
  val mcc: MessagesControllerComponents
  val assetsMetadata: AssetsMetadata
  lazy val healthController = wire[HealthController]
  lazy val templateWire = wire[Template]

  /* hacky way to allow the router to be overridden to the test-only router
   *
   * uses the underlying Config class, as `Configuration.getString` will throw an exception if `play.http.router` is not set
   *
   * can't use reflection (like `Router.load` does) as this needs to bind to specific wired instances of the routers
   */
  def router: Router =
    if (configuration.underlying.hasPath("play.http.router")) {
      configuration.getOptional[String]("play.http.router") match {
        case Some("testOnlyDoNotUseInAppConf.routes") | Some("testOnlyDoNotUseInAppConf.Routes") => testOnlyRoutes
        case _                                                                                   => prodRoutes
      }
    } else prodRoutes
}
