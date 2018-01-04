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
import sdil.actions.{AuthorisedAction, FormAction, RegisteredAction}
import sdil.connectors.SoftDrinksIndustryLevyConnector
import sdil.controllers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.bootstrap.http.{FrontendErrorHandler, HttpClient}

trait RoutesWiring extends CommonWiring {
  val errorHandler: FrontendErrorHandler
  val applicationLifecycle: DefaultApplicationLifecycle
  val httpClient: HttpClient
  val authConnector: AuthConnector
  val cache: SessionCache
  val sdilConnector: SoftDrinksIndustryLevyConnector

  lazy val authorisedAction: AuthorisedAction = wire[AuthorisedAction]
  lazy val formAction: FormAction = wire[FormAction]
  lazy val registeredAction: RegisteredAction = wire[RegisteredAction]
  lazy val assets: Assets = wire[Assets]
  lazy val servicePageController: ServicePageController = wire[ServicePageController]
  lazy val completeController: CompleteController = wire[CompleteController]
  lazy val orgTypeController: OrgTypeController = wire[OrgTypeController]
  lazy val identifyController: IdentifyController = wire[IdentifyController]
  lazy val verifyController: VerifyController = wire[VerifyController]
  lazy val litreageController: LitreageController = wire[LitreageController]
  lazy val packageCopackSmallVolumeController: PackageCopackSmallVolumeController = wire[PackageCopackSmallVolumeController]
  lazy val startDateController: StartDateController = wire[StartDateController]
  lazy val productionSiteController: ProductionSiteController = wire[ProductionSiteController]
  lazy val warehouseController: WarehouseController = wire[WarehouseController]
  lazy val contactDetailsController: ContactDetailsController = wire[ContactDetailsController]
  lazy val declarationController: DeclarationController = wire[DeclarationController]
  lazy val packageController: PackageController = wire[PackageController]
  lazy val radioFormController: RadioFormController = wire[RadioFormController]
  lazy val pendingController: PendingController = wire[PendingController]

  private lazy val appRoutes: app.Routes = wire[app.Routes]
  private lazy val healthRoutes = new health.Routes()
  private lazy val templateRoutes = new template.Routes()

  lazy val metrics: Metrics = wire[MetricsImpl]
  lazy val metricsController: MetricsController = wire[MetricsController]

  lazy val prefix: String = ""

  def router: prod.Routes = wire[prod.Routes]
}
