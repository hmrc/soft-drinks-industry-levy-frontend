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

import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.mockito.stubbing.OngoingStubbing
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.Retrieval
import uk.gov.hmrc.http.cache.client.{CacheMap, SessionCache}

import scala.concurrent.{ExecutionContext, Future}

package object controllerhelpers extends MockitoSugar {

  val mockAuthConnector: AuthConnector = mock[AuthConnector]

  val userWithUtr: Future[Option[String]] = Future successful Some("UTR")
  val userNoUtr: Future[Option[String]] = Future successful None

  def sdilAuthMock(returnValue: Future[Option[String]]): OngoingStubbing[Future[Option[String]]] =
    when(mockAuthConnector.authorise(any(), any[Retrieval[Option[String]]]())(any(), any[ExecutionContext]))
      .thenReturn(returnValue)

  val notLoggedIn: Future[Option[String]] = Future failed new InvalidBearerToken

  val validPackageForm = Seq(
    "isLiable" -> "true",
    "customers" -> "true",
    "ownBrands" -> "true")

  val validPackageFormCustomersOnly = Seq(
    "isLiable" -> "true",
    "customers" -> "true",
    "ownBrands" -> "false")

  val invalidPackageForm = Seq(
    "isLiable" -> "true",
    "customers" -> "false",
    "ownBrands" -> "false")

  val validContactDetailsForm = Seq(
    "fullName" -> "hello",
    "position" -> "boss",
    "phoneNumber" -> "+4411111111111",
    "email" -> "a@a.com"
  )

  val invalidContactDetailsForm = Seq(
    "fullName" -> "",
    "position" -> "boss",
    "phoneNumber" -> "+4411111111111",
    "email" -> "a@a.com"
  )
  lazy val mockCache = {
    val m = mock[SessionCache]
    when(m.cache(anyString(), any())(any(), any(), any())).thenReturn(Future.successful(CacheMap("", Map.empty)))
    m
  }

}
