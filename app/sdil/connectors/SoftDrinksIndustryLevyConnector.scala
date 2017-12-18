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

package sdil.connectors

import play.api.{Configuration, Environment}
import sdil.models._
import sdil.models.backend.Subscription
import sdil.models.sdilmodels._
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}

class SoftDrinksIndustryLevyConnector(http: HttpClient,
                                      environment: Environment,
                                      val runModeConfiguration: Configuration
                                     )(implicit ec: ExecutionContext) extends ServicesConfig {

  lazy val baseURL: String = baseUrl("soft-drinks-industry-levy")
  lazy val serviceURL = "hello-world"

  override protected def mode = environment.mode

  def retrieveHelloWorld()(implicit hc: HeaderCarrier): Future[DesSubmissionResult] = {
    http.GET[DesSubmissionResult](s"$baseURL/$serviceURL")
  }

  def submit(subscription: Subscription)(implicit hc: HeaderCarrier): Future[Unit] = {
    http.POST[Subscription, HttpResponse](s"$baseURL/subscription/utr/${subscription.utr}", subscription) map { _ => () }
  }

}
