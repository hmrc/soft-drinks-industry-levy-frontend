/*
 * Copyright 2022 HM Revenue & Customs
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
import play.api.libs.json._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import sdil.models._
import sdil.models.backend._
import sdil.models.retrieved._
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.allEnrolments
import uk.gov.hmrc.http.cache.client.ShortLivedHttpCaching
import uk.gov.hmrc.http.{CoreDelete, CoreGet, CorePut, HeaderCarrier}
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
      when(mockSdilConnector.returns_pending(matching("0000000022"))(any()))
        .thenReturn(Future.successful(List.empty[ReturnPeriod]))

      val result = controller.index(2018, 1, nilReturn = false, "idvalue").apply(FakeRequest())
      status(result) mustEqual SEE_OTHER
      redirectLocation(result) mustEqual Some(value = routes.ServicePageController.show().url)
    }

    // TODO: Fix it
    "execute index journey" ignore {
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
      redirectLocation(result) mustEqual Some(value = routes.ServicePageController.show().url)
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

  }

  lazy val shortLivedCaching: ShortLivedHttpCaching = new ShortLivedHttpCaching {
    override def baseUri: String = ???
    override def domain: String = ???
    override def defaultSource: String = ???
    override def http: CoreGet with CorePut with CoreDelete = ???
  }
  lazy val hc: HeaderCarrier = HeaderCarrier()

  // DATA OUT:Map(claim-credits-for-exports -> {"lower":6789,"higher":2345}, packaged-as-a-contract-packer -> {"lower":1234579,"higher":2345679}, claim-credits-for-lost-damaged -> {"lower":123,"higher":234}, brought-into-uk-from-small-producers -> {"lower":1234,"higher":2345}, _editSmallProducers -> false, own-brands-packaged-at-own-sites -> {"lower":123234,"higher":2340000}, small-producer-details -> "Done", return-change-registration -> null, brought-into-uk -> {"lower":1234562,"higher":2345672}, ask-secondary-warehouses-in-return -> false, exemptions-for-small-producers -> false)

}
