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

package sdil.controllers

import org.mockito.ArgumentMatchers.{any, eq => matching}
import org.mockito.Mockito.when
import org.mockito.stubbing.OngoingStubbing
import org.scalatestplus.play.{BaseOneAppPerSuite, FakeApplicationFactory, PlaySpec}
import play.api.{Application, ApplicationLoader}
import play.core.DefaultWebCommands
import sdil.config.SDILApplicationLoader
import sdil.utils.TestWiring
import uk.gov.hmrc.auth.core.InvalidBearerToken
import uk.gov.hmrc.auth.core.retrieve.Retrieval

import scala.concurrent.{ExecutionContext, Future}

trait ControllerSpec extends PlaySpec with BaseOneAppPerSuite with FakeApplicationFactory with TestWiring {
  override def fakeApplication: Application = {
    val context = ApplicationLoader.Context(
      environment = env,
      sourceMapper = None,
      webCommands = new DefaultWebCommands,
      initialConfiguration = configuration
    )
    val loader = new SDILApplicationLoader
    loader.load(context)
  }

  def stubCacheEntry[T](key: String, value: Option[T]) = {
    when(mockCache.fetchAndGetEntry[T](matching(key))(any(), any(), any())).thenReturn(Future.successful(value))
  }

  def sdilAuthMock(returnValue: Future[Option[String]]): OngoingStubbing[Future[Option[String]]] =
    when(mockAuthConnector.authorise(any(), any[Retrieval[Option[String]]]())(any(), any[ExecutionContext]))
      .thenReturn(returnValue)

  val userWithUtr: Future[Option[String]] = Future successful Some("UTR")
  val userNoUtr: Future[Option[String]] = Future successful None

  val notLoggedIn: Future[Option[String]] = Future failed new InvalidBearerToken

}
