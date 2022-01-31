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
import play.api.test.FakeRequest
import play.api.test.Helpers._
import sdil.journeys.instances
import sdil.models.Address
import sdil.models.backend.UkAddress
import sdil.models.variations.RegistrationVariationData
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.allEnrolments
import uk.gov.hmrc.http.cache.client.CacheMap

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

class VariationsControllerSpec extends ControllerSpec {

  val controller = new VariationsController(
    stubMessagesControllerComponents,
    testConfig,
    uniformHelpers,
    registeredAction,
    mockSdilConnector,
    mockCache,
    mockRegVariationsCache,
    mockRetVariationsCache
  )

  "VariationsController" should {

    "when a user isn't enrolled within the change address and contact they are redirected to start of registration" in {
      val sdilEnrolment = EnrolmentIdentifier("", "")
      when(mockAuthConnector.authorise[Enrolments](any(), matching(allEnrolments))(any(), any())).thenReturn {
        Future.successful(Enrolments(Set(Enrolment("HMRC-OBTDS-ORG", Seq(sdilEnrolment), "Active"))))
      }
      val result = controller.changeAddressAndContact("idvalue").apply(FakeRequest())
      status(result) mustEqual SEE_OTHER
      redirectLocation(result) mustEqual Some("/soft-drinks-industry-levy/register/start")
    }

    "when a user isn't subscribed and enrolled within the change address and contact they are taken to not found" in {
      val sdilEnrolment = EnrolmentIdentifier("EtmpRegistrationNumber", "XCSDIL000000002")
      when(mockAuthConnector.authorise[Enrolments](any(), matching(allEnrolments))(any(), any())).thenReturn {
        Future.successful(Enrolments(Set(Enrolment("HMRC-OBTDS-ORG", Seq(sdilEnrolment), "Active"))))
      }
      val result = controller.changeAddressAndContact("idvalue").apply(FakeRequest())
      status(result) mustEqual NOT_FOUND
    }

    "When a user has enrolled with no subscirption they are directed to the first step in the change business address journey" in {
      val sdilEnrolment = EnrolmentIdentifier("EtmpRegistrationNumber", "XZSDIL000100107")
      when(mockAuthConnector.authorise[Enrolments](any(), matching(allEnrolments))(any(), any())).thenReturn {
        Future.successful(Enrolments(Set(Enrolment("HMRC-OBTDS-ORG", Seq(sdilEnrolment), "Active"))))
      }
      val result = controller.changeAddressAndContact("idvalue").apply(FakeRequest())
      status(result) mustEqual SEE_OTHER
      redirectLocation(result) mustEqual Some("change-business-address")
    }

    "When a user has enrolled without a subscirption " in {
      val sdilEnrolment = EnrolmentIdentifier("EtmpRegistrationNumber", "")
      when(mockAuthConnector.authorise[Enrolments](any(), matching(allEnrolments))(any(), any())).thenReturn {
        Future.successful(Enrolments(Set(Enrolment("HMRC-OBTDS-ORG", Seq(sdilEnrolment), "Active"))))
      }

      when(mockSdilConnector.retrieveSubscription(matching(""), anyString())(any())).thenReturn {
        Future.successful(None)
      }

      val result = controller.changeAddressAndContact("idvalue").apply(FakeRequest())
      status(result) mustEqual SEE_OTHER
    }

    //TODO
    "When a user has enrolled with a subscription and has started the journey they are taken to the change business address page" in {
      val sdilEnrolment = EnrolmentIdentifier("EtmpRegistrationNumber", "XZSDIL000100107")
      when(mockAuthConnector.authorise[Enrolments](any(), matching(allEnrolments))(any(), any())).thenReturn {
        Future.successful(Enrolments(Set(Enrolment("HMRC-OBTDS-ORG", Seq(sdilEnrolment), "Active"))))
      }

      val result = controller.changeAddressAndContact("idvalue").apply(FakeRequest())
      status(result) mustEqual SEE_OTHER
      redirectLocation(result) mustEqual Some("change-business-address")
    }

    "When a user is not enrolled they are taken to the start of the registration" in {

      val sdilEnrolment = EnrolmentIdentifier("", "")
      when(mockAuthConnector.authorise[Enrolments](any(), matching(allEnrolments))(any(), any())).thenReturn {
        Future.successful(Enrolments(Set(Enrolment("HMRC-OBTDS-ORG", Seq(sdilEnrolment), "Active"))))
      }

      val result = controller.index("idvalue").apply(FakeRequest())
      status(result) mustEqual SEE_OTHER
      redirectLocation(result) mustEqual Some("/soft-drinks-industry-levy/register/start")
    }

    "redirect to not found page when subscrition doesn't exist" in {

      val sdilEnrolment = EnrolmentIdentifier("EtmpRegistrationNumber", "XCSDIL000000002")
      when(mockAuthConnector.authorise[Enrolments](any(), matching(allEnrolments))(any(), any())).thenReturn {
        Future.successful(Enrolments(Set(Enrolment("HMRC-OBTDS-ORG", Seq(sdilEnrolment), "Active"))))
      }

      val result = controller.index("idvalue").apply(FakeRequest())
      status(result) mustEqual NOT_FOUND
    }

    "When a user is enrolled with subscription they are taken to first page within the variations journey" in {

      val sdilEnrolment = EnrolmentIdentifier("EtmpRegistrationNumber", "XZSDIL000100107")
      when(mockAuthConnector.authorise[Enrolments](any(), matching(allEnrolments))(any(), any())).thenReturn {
        Future.successful(Enrolments(Set(Enrolment("HMRC-OBTDS-ORG", Seq(sdilEnrolment), "Active"))))
      }

      val result = controller.index("idvalue").apply(FakeRequest())
      status(result) mustEqual SEE_OTHER
      redirectLocation(result) mustEqual Some("select-change")
    }

    //TODO
    "redirect to " in {

      val sdilEnrolment = EnrolmentIdentifier("EtmpRegistrationNumber", "XZSDIL000100107")
      when(mockAuthConnector.authorise[Enrolments](any(), matching(allEnrolments))(any(), any())).thenReturn {
        Future.successful(Enrolments(Set(Enrolment("HMRC-OBTDS-ORG", Seq(sdilEnrolment), "Active"))))
      }

      val result = controller.index("").apply(FakeRequest())
      status(result) mustEqual SEE_OTHER
      redirectLocation(result) mustEqual Some("/select-change")
    }

    //TODO NOT PASSING LINE 197
    "show variations complete" in {
      val sdilRef = "XKSDIL000000040"
      val sdilEnrolment = EnrolmentIdentifier("EtmpRegistrationNumber", sdilRef)
      when(mockAuthConnector.authorise[Enrolments](any(), matching(allEnrolments))(any(), any())).thenReturn {
        Future.successful(Enrolments(Set(Enrolment("HMRC-OBTDS-ORG", Seq(sdilEnrolment), "Active"))))
      }

      when(mockSdilConnector.retrieveSubscription(matching("XCSDIL000000002"), anyString())(any())).thenReturn {
        Future.successful(Some(aSubscription))
      }
      when(mockRegVariationsCache.get(matching("XCSDIL000000002"))(any()))
        .thenReturn(Future.successful(None))

      when(mockRetVariationsCache.get(matching("internal id"))(any())).thenReturn(Future.successful(None))

      when(mockSdilConnector.retrieveSubscription(matching(irCtEnrolment.value), anyString())(any())).thenReturn {
        Future.successful(Some(aSubscription))

      }
      when(mockSdilConnector.balanceHistory(matching("XCSDIL000000002"), any())(any()))
        .thenReturn(Future.successful(List()))

      val request = FakeRequest().withFormUrlEncodedBody("sdilEnrolment" -> sdilRef, "utr" -> "0000000033")
      val result = controller.showVariationsComplete().apply(request)
      status(result) mustEqual OK
    }

    "change actor status" in {
      val result =
        controller
          .changeActorStatus("")
          .apply(FakeRequest())
      status(result) mustEqual 303
    }
  }
}
