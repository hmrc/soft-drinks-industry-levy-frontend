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

import java.time.{Clock, Instant, ZoneId}

import org.mockito.ArgumentMatchers.{eq => matching, _}
import org.mockito.Mockito._
import org.mockito.stubbing.OngoingStubbing
import org.scalatest.mockito.MockitoSugar
import sdil.models.Packaging
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

  val validStartDateForm = Seq(
    "startDateDay" -> "06",
    "startDateMonth" -> "04",
    "startDateYear" -> "2018"
  )

  val invalidStartDateForm = Seq(
    "startDateDay" -> "29",
    "startDateMonth" -> "02",
    "startDateYear" -> "2017"
  )
  val invalidStartDateDayForm = Seq(
    "startDateDay" -> "35",
    "startDateMonth" -> "08",
    "startDateYear" -> "2017"
  )
  val invalidStartDateMonthForm = Seq(
    "startDateDay" -> "20",
    "startDateMonth" -> "30",
    "startDateYear" -> "2017"
  )
  val invalidStartDateYearForm = Seq(
    "startDateDay" -> "20",
    "startDateMonth" -> "02",
    "startDateYear" -> "20189"
  )
  val packagingIsLiable = Packaging(true, true, false)
  val packagingIsntLiable = Packaging(false, false, false)

  lazy val mockCache = {
    val m = mock[SessionCache]
    when(m.cache(anyString(), any())(any(), any(), any())).thenReturn(Future.successful(CacheMap("", Map.empty)))
    when(m.fetchAndGetEntry(any())(any(), any(), any())).thenReturn(Future.successful(None))
    m
  }

  def stubCacheEntry[T](key: String, value: Option[T]) = {
    when(mockCache.fetchAndGetEntry[T](matching(key))(any(), any(), any())).thenReturn(Future.successful(value))
  }

  lazy val dateBeforeTaxStart = Clock.fixed(Instant.parse("2017-11-18T14:19:00.000Z"), ZoneId.systemDefault())

  lazy val dateAfterTaxStart = Clock.fixed(Instant.parse("2018-04-07T00:00:00.000Z"), ZoneId.systemDefault())

}
