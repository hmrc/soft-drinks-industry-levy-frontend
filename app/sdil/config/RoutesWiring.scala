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

import com.kenshoo.play.metrics.{Metrics, MetricsController, MetricsImpl}
import com.softwaremill.macwire.wire
import controllers.Assets
import play.api.inject.DefaultApplicationLifecycle
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

  lazy val assets: Assets = wire[Assets]
  lazy val sdilController: SDILController = wire[SDILController]
  lazy val identifyController: IdentifyController = wire[IdentifyController]
  lazy val verifyController: VerifyController = wire[VerifyController]
  lazy val litreageController: LitreageController = wire[LitreageController]
  lazy val packageCopackController: PackageCopackSmallController = wire[PackageCopackSmallController]
  lazy val copackedController: CopackedController = wire[CopackedController]
  lazy val importController: ImportController = wire[ImportController]
  lazy val startDateController: StartDateController = wire[StartDateController]
  lazy val productionSiteController: ProductionSiteController = wire[ProductionSiteController]
  lazy val warehouseController: WarehouseController = wire[WarehouseController]
  lazy val contactDetailsController: ContactDetailsController = wire[ContactDetailsController]

  private lazy val appRoutes: app.Routes = wire[app.Routes]
  private lazy val healthRoutes = new health.Routes()
  private lazy val templateRoutes = new template.Routes()

  lazy val metrics: Metrics = wire[MetricsImpl]
  lazy val metricsController: MetricsController = wire[MetricsController]

  lazy val prefix: String = ""

  def router: prod.Routes = wire[prod.Routes]
}
