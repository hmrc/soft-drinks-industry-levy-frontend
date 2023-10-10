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

package sdil.controllers

import org.mockito.ArgumentMatchers.{any, eq => matching, _}
import org.mockito.Mockito._
import play.api.data
import play.api.test.FakeRequest
import play.api.test.Helpers._
import sdil.models._
import sdil.utils.TestConfig
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.allEnrolments
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.CacheMap

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ReturnsControllerSpec extends ControllerSpec {

  val controller = new ReturnsController(
    stubMessagesControllerComponents,
    testConfig,
    uniformHelpers,
    registeredAction,
    mockSdilConnector,
    mockCache,
    mockReturnsCache
  )

  "ReturnsController" should {

    "redirect to service page when no pending returns for period" in {
      val sdilEnrolment = EnrolmentIdentifier("EtmpRegistrationNumber", "XZSDIL000100107")
      when(mockAuthConnector.authorise[Enrolments](any(), matching(allEnrolments))(any(), any())).thenReturn {
        Future.successful(Enrolments(Set(Enrolment("HMRC-OBTDS-ORG", Seq(sdilEnrolment), "Active"))))
      }
      when(mockSdilConnector.retrieveSubscription(matching("XCSDIL000000002"), anyString())(any())).thenReturn {
        Future.successful(Some(aSubscription))
      }

      when(cacheMock.cache(anyString(), anyString(), any())(any(), any(), any()))
        .thenReturn(Future.successful(CacheMap("", Map.empty)))

      when(mockSdilConnector.returns_pending(matching("0000000022"))(any()))
        .thenReturn(Future.successful(List.empty[ReturnPeriod]))

      val result = controller.index(2018, 1, nilReturn = false, "idvalue").apply(FakeRequest())
      status(result) mustEqual SEE_OTHER
      redirectLocation(result) mustEqual Some("/soft-drinks-industry-levy")
    }

    // TODO: Fix it
    "execute index journey" in {
      val sdilEnrolment = EnrolmentIdentifier("EtmpRegistrationNumber", "XZSDIL000100107")
      when(mockAuthConnector.authorise[Enrolments](any(), matching(allEnrolments))(any(), any())).thenReturn {
        Future.successful(Enrolments(Set(Enrolment("HMRC-OBTDS-ORG", Seq(sdilEnrolment), "Active"))))
      }
      when(mockSdilConnector.retrieveSubscription(matching("XCSDIL000000002"), anyString())(any())).thenReturn {
        Future.successful(Some(aSubscription))
      }
      when(mockSdilConnector.returns_pending(matching("0000000022"))(any()))
        .thenReturn(Future.successful(returnPeriods))

      val result = controller.index(2018, 1, nilReturn = false, "idvalue").apply(FakeRequest())
      status(result) mustEqual SEE_OTHER
      redirectLocation(result) mustEqual Some("/soft-drinks-industry-levy") //Some("own-brands-packaged-at-own-sites")
    }

    "execute index journey and throw a NoSuchElementException" in {
      val sdilEnrolment = EnrolmentIdentifier("EtmpRegistrationNumber", "XZSDIL000100107")
      when(mockAuthConnector.authorise[Enrolments](any(), matching(allEnrolments))(any(), any())).thenReturn {
        Future.successful(Enrolments(Set(Enrolment("HMRC-OBTDS-ORG", Seq(sdilEnrolment), "Active"))))
      }
      when(mockSdilConnector.retrieveSubscription(matching("XCSDIL000000002"), anyString())(any())).thenReturn {
        Future.successful(Some(aSubscription))
      }
      when(mockSdilConnector.returns_pending(matching("0000000022"))(any()))
        .thenThrow(new NoSuchElementException("Exception occurred while retrieving pendingReturns"))

      val result = controller.index(2018, 1, nilReturn = false, "idvalue").apply(FakeRequest())
      status(result) mustEqual SEE_OTHER

    }

    "execute index journey with a nil return" in {
      val sdilEnrolment = EnrolmentIdentifier("EtmpRegistrationNumber", "XZSDIL000100107")
      when(mockAuthConnector.authorise[Enrolments](any(), matching(allEnrolments))(any(), any())).thenReturn {
        Future.successful(Enrolments(Set(Enrolment("HMRC-OBTDS-ORG", Seq(sdilEnrolment), "Active"))))
      }
      when(mockSdilConnector.retrieveSubscription(matching("XCSDIL000000002"), anyString())(any())).thenReturn {
        Future.successful(Some(aSubscription))
      }
      when(mockSdilConnector.returns_pending(matching("0000000022"))(any()))
        .thenReturn(Future.successful(returnPeriods))

      val result = controller.index(2018, 1, true, "idvalue").apply(FakeRequest())
      status(result) mustEqual SEE_OTHER

    }

    "execute index journey with a nil return and no pending returns" in {
      val sdilEnrolment = EnrolmentIdentifier("EtmpRegistrationNumber", "XZSDIL000100107")
      when(mockAuthConnector.authorise[Enrolments](any(), matching(allEnrolments))(any(), any())).thenReturn {
        Future.successful(Enrolments(Set(Enrolment("HMRC-OBTDS-ORG", Seq(sdilEnrolment), "Active"))))
      }
      when(mockSdilConnector.retrieveSubscription(matching("XCSDIL000000002"), anyString())(any())).thenReturn {
        Future.successful(Some(aSubscription))
      }
      when(mockSdilConnector.returns_pending(matching("0000000022"))(any())).thenReturn(Future.successful((Nil)))

      val result = controller.index(2018, 1, true, "idvalue").apply(FakeRequest())
      status(result) mustEqual SEE_OTHER

    }

    "execute showReturnComplete with" in {
      when(mockReturnsCache.get(matching(""))(any()))
        .thenReturn(Future.successful(Some(mockreturnFormData)))

      val sdilEnrolment = EnrolmentIdentifier("EtmpRegistrationNumber", "XZSDIL000100107")
      when(mockAuthConnector.authorise[Enrolments](any(), matching(allEnrolments))(any(), any())).thenReturn {
        Future.successful(Enrolments(Set(Enrolment("HMRC-OBTDS-ORG", Seq(sdilEnrolment), "Active"))))
      }

      when(mockSdilConnector.returns_pending(matching("0000000022"))(any()))
        .thenReturn(Future.successful(returnPeriods))

      when(mockSdilConnector.retrieveSubscription(matching("XCSDIL000000002"), anyString())(any())).thenReturn {
        Future.successful(Some(aSubscription))
      }

      when(mockSdilConnector.checkSmallProducerStatus(matching("XZSDIL000100107"), any())(any())).thenReturn {
        Future.successful(Some(true))
      }

      val result = controller.showReturnComplete(2018, 1).apply(FakeRequest())
      status(result) mustEqual SEE_OTHER
    }

    "redirect to new returns frontend when redirect to new returns flag is true" in {
      val config = new TestConfig(configuration) {
        override val redirectToNewReturnsEnabled: Boolean = true
      }
      val sdilEnrolment = EnrolmentIdentifier("EtmpRegistrationNumber", "XZSDIL000100107")
      when(mockAuthConnector.authorise[Enrolments](any(), matching(allEnrolments))(any(), any())).thenReturn {
        Future.successful(Enrolments(Set(Enrolment("HMRC-OBTDS-ORG", Seq(sdilEnrolment), "Active"))))
      }
      when(mockSdilConnector.retrieveSubscription(matching("XCSDIL000000002"), anyString())(any())).thenReturn {
        Future.successful(Some(aSubscription))
      }
      when(mockSdilConnector.returns_pending(matching("0000000022"))(any())).thenReturn(Future.successful(any))

      val result = controller.index(2018, 1, false, "idvalue").apply(FakeRequest())
      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustBe "http://localhost:8703/soft-drinks-industry-levy-returns-frontend/submit-return/year/2018/quarter/1/nil-return/false"

    }

  }
  lazy val hc: HeaderCarrier = HeaderCarrier()
}
