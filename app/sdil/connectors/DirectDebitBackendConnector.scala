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

package sdil.connectors

import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.http.HttpReads.Implicits.readFromJson

import scala.concurrent.{ExecutionContext, Future}

class DirectDebitBackendConnector(http: HttpClient, config: ServicesConfig)(implicit ec: ExecutionContext) {

  lazy val directDebitBaseUrl: String = s"${config.baseUrl("direct-debit-backend")}/direct-debit-backend"

  def getSdilDirectDebitLink(request: StartSdilReturnFromSdilFrontend)(
    implicit headerCarrier: HeaderCarrier): Future[DirectDebitNextUrl] =
    http.POST[StartSdilReturnFromSdilFrontend, DirectDebitNextUrl](
      s"$directDebitBaseUrl/sdil-frontend/zsdl/journey/start",
      request
    )
}

final case class StartSdilReturnFromSdilFrontend(returnUrl: String, backUrl: String)

object StartSdilReturnFromSdilFrontend {
  implicit val format: Format[StartSdilReturnFromSdilFrontend] = Json.format[StartSdilReturnFromSdilFrontend]
}

final case class DirectDebitNextUrl(nextUrl: String)

object DirectDebitNextUrl {
  implicit val nextUrlFormat: Format[DirectDebitNextUrl] = Json.format[DirectDebitNextUrl]
}
