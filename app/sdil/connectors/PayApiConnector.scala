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

package sdil.connectors

import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

class PayApiConnector(http: HttpClient, config: ServicesConfig) {

  lazy val payApiBaseUrl: String = s"${config.baseUrl("pay-api")}/pay-api"

  def getSdilPayLink(
    spjRequest: SpjRequestBtaSdil)(implicit headerCarrier: HeaderCarrier, ec: ExecutionContext): Future[NextUrl] =
    http.POST[SpjRequestBtaSdil, NextUrl](s"$payApiBaseUrl/bta/sdil/journey/start", spjRequest)
}

final case class SpjRequestBtaSdil(
  reference: String,
  amountInPence: Long,
  returnUrl: String,
  backUrl: String
)

object SpjRequestBtaSdil {
  implicit val format: Format[SpjRequestBtaSdil] = Json.format[SpjRequestBtaSdil]
}

final case class NextUrl(nextUrl: String)

object NextUrl {
  implicit val nextUrlFormat: Format[NextUrl] = Json.format[NextUrl]
}
