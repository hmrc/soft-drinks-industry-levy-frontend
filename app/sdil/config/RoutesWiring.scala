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

import com.kenshoo.play.metrics.{Metrics, MetricsController, MetricsImpl}
import com.softwaremill.macwire.wire
import controllers.Assets
import play.api.inject.DefaultApplicationLifecycle
import play.api.routing.Router
import sdil.actions.{AuthorisedAction, FormAction, RegisteredAction, VariationAction}
import sdil.connectors.{ContactFrontendConnector, GaConnector, SoftDrinksIndustryLevyConnector, TestConnector}
import sdil.controllers.{VariationsController => UniformVariationsController, _}
import sdil.controllers.test.TestingController
import sdil.controllers.variation._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.bootstrap.http.{FrontendErrorHandler, HttpClient}

trait RoutesWiring extends CommonWiring {
  val errorHandler: FrontendErrorHandler
  val applicationLifecycle: DefaultApplicationLifecycle
  val httpClient: HttpClient
  val authConnector: AuthConnector
  val cache: RegistrationFormDataCache
  val sdilConnector: SoftDrinksIndustryLevyConnector
  val testConnector: TestConnector
  val gaConnector: GaConnector
  val contactFrontendConnector: ContactFrontendConnector
  val keystore: SessionCache

  lazy val authorisedAction: AuthorisedAction = wire[AuthorisedAction]
  lazy val formAction: FormAction = wire[FormAction]
  lazy val registeredAction: RegisteredAction = wire[RegisteredAction]
  lazy val variationAction: VariationAction = wire[VariationAction]
  lazy val assets: Assets = wire[Assets]
  lazy val servicePageController: ServicePageController = wire[ServicePageController]
  lazy val completeController: CompleteController = wire[CompleteController]
  lazy val organisationTypeController: OrganisationTypeController = wire[OrganisationTypeController]
  lazy val identifyController: IdentifyController = wire[IdentifyController]
  lazy val verifyController: VerifyController = wire[VerifyController]
  lazy val litreageController: LitreageController = wire[LitreageController]
  lazy val registrationTypeController: RegistrationNotRequiredController = wire[RegistrationNotRequiredController]
  lazy val startDateController: StartDateController = wire[StartDateController]
  lazy val productionSiteController: ProductionSiteController = wire[ProductionSiteController]
  lazy val warehouseController: WarehouseController = wire[WarehouseController]
  lazy val contactDetailsController: ContactDetailsController = wire[ContactDetailsController]
  lazy val declarationController: DeclarationController = wire[DeclarationController]
  lazy val radioFormController: RadioFormController = wire[RadioFormController]
  lazy val signoutController: AuthenticationController = wire[AuthenticationController]
  lazy val testController: TestingController = wire[TestingController]
  lazy val registerForBetaController: RegisterForBetaController = wire[RegisterForBetaController]
  lazy val producerController: ProducerController = wire[ProducerController]
  lazy val variationsController: VariationsController = wire[VariationsController]
  lazy val businessDetailsController: BusinessDetailsController = wire[BusinessDetailsController]
  lazy val producerVariationsController: ProducerVariationsController = wire[ProducerVariationsController]
  lazy val usesCopackerController: UsesCopackerController = wire[UsesCopackerController]
  lazy val packageOwnController: PackageOwnController = wire[PackageOwnController]
  lazy val packageOwnVolController: PackageOwnVolController = wire[PackageOwnVolController]
  lazy val copackForOthersController: CopackForOthersController = wire[CopackForOthersController]
  lazy val copackForOthersVolController: CopackForOthersVolController = wire[CopackForOthersVolController]
  lazy val importsController: ImportsController = wire[ImportsController]
  lazy val importsVolController: ImportsVolController = wire[ImportsVolController]
  lazy val contactDetailsVariationController: ContactDetailsVariationController = wire[ContactDetailsVariationController]
  lazy val warehouseVariationController: WarehouseVariationController = wire[WarehouseVariationController]
  lazy val productionSiteVariationController: ProductionSiteVariationController = wire[ProductionSiteVariationController]
  lazy val variationsSummaryController: VariationsSummaryController = wire[VariationsSummaryController]

  lazy val uniformVariationsController: UniformVariationsController = wire[UniformVariationsController]
  lazy val returnsController: ReturnsController = wire[ReturnsController]

  private lazy val appRoutes: app.Routes = wire[app.Routes]
  private lazy val healthRoutes = new health.Routes()
  private lazy val templateRoutes = new template.Routes()
  private lazy val prodRoutes: prod.Routes = wire[prod.Routes]
  private lazy val variationsRoutes: variations.Routes = wire[variations.Routes]

  private lazy val testOnlyRoutes: testOnlyDoNotUseInAppConf.Routes = wire[testOnlyDoNotUseInAppConf.Routes]

  lazy val metrics: Metrics = wire[MetricsImpl]
  lazy val metricsController: MetricsController = wire[MetricsController]

  lazy val prefix: String = ""

  /* hacky way to allow the router to be overridden to the test-only router
   *
   * uses the underlying Config class, as `Configuration.getString` will throw an exception if `play.http.router` is not set
   *
   * can't use reflection (like `Router.load` does) as this needs to bind to specific wired instances of the routers
   */
  def router: Router = {
    if (configuration.underlying.hasPath("play.http.router")) {
      configuration.getString("play.http.router") match {
        case Some("testOnlyDoNotUseInAppConf.routes") | Some("testOnlyDoNotUseInAppConf.Routes") => testOnlyRoutes
        case _ => prodRoutes
      }
    } else prodRoutes
  }
}
