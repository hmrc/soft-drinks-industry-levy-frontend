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

import akka.util.ByteString
import play.api.libs.ws.WSClient
import play.api.{Configuration, Environment}
import play.twirl.api.Html
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.RunMode
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails

import scala.concurrent.Future

class TestConnector(http: HttpClient,
                    environment: Environment,
                    ws: WSClient,
                    val runModeConfiguration: Configuration,
                    val runMode: RunMode) extends ServicesConfig(runModeConfiguration, runMode) {

  lazy val testUrl: String = baseUrl("soft-drinks-industry-levy")

  def reset(url: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    http.GET[HttpResponse](s"$testUrl/test-only/$url")
  }

  def getFile(envelopeId: String, fileName: String)(implicit hc: HeaderCarrier): Future[ByteString] = {
    ws.url(s"$testUrl/test-only/get-file/$envelopeId/$fileName").get().map(_.bodyAsBytes)
  }

  def getVariationHtml(sdilRef: String)(implicit hc: HeaderCarrier): Future[Option[Html]] = {
    http.GET[HttpResponse](s"$testUrl/test-only/get-last-variation/$sdilRef") map {
      case res if res.status == 200 => Some(Html(res.body))
      case _ => None
    }
  }

}
