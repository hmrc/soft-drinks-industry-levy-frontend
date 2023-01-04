/*
 * Copyright 2023 HM Revenue & Customs
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

import play.api.libs.json.Json
import play.api.{Configuration, Environment, Logging}
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.http.HttpReads.Implicits.readRaw

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class GaConnector @Inject()(http: HttpClient, val configuration: Configuration)
    extends ServicesConfig(configuration) with Logging {

  implicit val dimensionWrites = Json.writes[DimensionValue]
  implicit val eventWrites = Json.writes[Event]
  implicit val analyticsWrites = Json.writes[AnalyticsRequest]

  val serviceUrl: String = s"${baseUrl("platform-analytics")}/platform-analytics/event"

  def sendEvent(request: AnalyticsRequest)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    http.POST(serviceUrl, request).map(_ => ()) recover {
      case e: Exception => logger.error(s"Couldn't send analytics event $request", e)
    }
}

case class DimensionValue(index: Int, value: String)

case class Event(
  category: String,
  action: String,
  label: String,
  dimensions: Seq[DimensionValue] = Nil,
  userId: Option[String] = None)

case class AnalyticsRequest(gaClientId: String, events: Seq[Event])
