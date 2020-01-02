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

import akka.stream.Materializer
import com.softwaremill.macwire.wire
import play.api.i18n.MessagesApi
import play.api.{Configuration, Environment, Mode}
import uk.gov.hmrc.http.cache.client.ShortLivedHttpCaching
import uk.gov.hmrc.play.bootstrap.config.RunMode
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.ExecutionContext

trait CommonWiring {
  val configuration: Configuration
  val environment: Environment
  val messagesApi: MessagesApi
  val httpClient: HttpClient
  implicit val ec: ExecutionContext
  implicit val appConfig: AppConfig
  implicit val materializer: Materializer
  lazy val runMode: RunMode = wire[RunMode]
  lazy val mode: Mode = environment.mode
  lazy val shortLivedCaching: ShortLivedHttpCaching = wire[SDILShortLivedCaching]
  lazy val keystore: SDILSessionCache = wire[SDILSessionCache]
}
