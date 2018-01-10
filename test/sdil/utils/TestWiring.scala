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

package sdil.utils

import java.io.File

import com.softwaremill.macwire.MacwireMacros
import org.mockito.ArgumentMatchers.{any, anyString}
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import play.api.i18n.{DefaultLangs, DefaultMessagesApi, Messages, MessagesApi}
import play.api.libs.concurrent.Execution.defaultContext
import play.api.test.FakeRequest
import play.api.{Configuration, Environment}
import sdil.actions.{AuthorisedAction, FormAction}
import sdil.config.FormDataCache
import sdil.connectors.SoftDrinksIndustryLevyConnector
import uk.gov.hmrc.auth.core.AffinityGroup.Organisation
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.bootstrap.http.FrontendErrorHandler

import scala.concurrent.{ExecutionContext, Future}
import scala.language.experimental.macros

trait TestWiring extends MockitoSugar {
  //local alias for macwire macro to avoid having to import it manually everywhere
  def wire[T]: T = macro MacwireMacros.wire_impl[T]

  val mockCache: FormDataCache = {
    val m = mock[FormDataCache]
    when(m.cache(anyString(), any())(any(), any())).thenReturn(Future.successful(CacheMap("", Map.empty)))
    when(m.get(anyString())(any(), any())).thenReturn(Future.successful(None))
    when(m.clear(anyString())(any(), any())).thenReturn(Future.successful(()))
    m
  }

  implicit lazy val testConfig: TestConfig = new TestConfig

  lazy val mockErrorHandler = mock[FrontendErrorHandler]

  lazy val env: Environment = Environment.simple(new File("."))
  //scala will not compile implicit conversion from Boolean → AnyRef
  lazy val configuration: Configuration = Configuration.load(env, Map("metrics.enabled" -> false.asInstanceOf[AnyRef]))

  val messagesApi: MessagesApi = new DefaultMessagesApi(env, configuration, new DefaultLangs(configuration))
  implicit val defaultMessages: Messages = messagesApi.preferred(FakeRequest())
  implicit val ec: ExecutionContext = defaultContext

  lazy val mockSdilConnector: SoftDrinksIndustryLevyConnector = {
    val m = mock[SoftDrinksIndustryLevyConnector]
    when(m.submit(any(),any())(any())).thenReturn(Future.successful(()))
    m
  }

  type Retrieval = Enrolments ~ Option[CredentialRole] ~ Option[String] ~ Option[AffinityGroup]

  lazy val mockAuthConnector: AuthConnector = {
    val m = mock[AuthConnector]
    when(m.authorise[Retrieval](any(), any())(any(), any())).thenReturn {
      Future.successful(new ~(new ~(new ~(Enrolments(Set.empty), Some(Admin)), Some("internal id")), Some(Organisation)))
    }
    m
  }

  lazy val formAction: FormAction = wire[FormAction]
  lazy val authorisedAction: AuthorisedAction = wire[AuthorisedAction]
}
