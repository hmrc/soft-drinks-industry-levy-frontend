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

package sdil.controllers

import com.softwaremill.macwire._
import org.mockito.ArgumentMatchers.{eq => matching, _}
import org.mockito.Mockito._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import sdil.connectors.{NextUrl, SpjRequestBtaSdil}
import uk.gov.hmrc.auth.core.{Enrolment, EnrolmentIdentifier, Enrolments}

import scala.concurrent.Future

class PaymentControllerSpec extends ControllerSpec {

  "PaymentController" should {

    lazy val testController = wire[PaymentController]

    val testRedirectUrl = "/test-url"
    val testSdilEnrolment = EnrolmentIdentifier("EtmpRegistrationNumber", "XKSDIL000000033")

    "contact pay-api and get a redirect url" in {
      val testPayApiRequest = SpjRequestBtaSdil("XKSDIL000000033", 0L, testConfig.sdilHomePage, testConfig.sdilHomePage)

      when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any())).thenReturn {
        Future.successful(Enrolments(Set(Enrolment("HMRC-OBTDS-ORG", Seq(testSdilEnrolment), "Active"))))
      }

      when(mockPayApiConnector.getSdilPayLink(matching(testPayApiRequest))(any(), any()))
        .thenReturn(Future.successful(NextUrl(testRedirectUrl)))

      val result = testController.payNow()(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(testRedirectUrl)

      verify(mockPayApiConnector).getSdilPayLink(matching(testPayApiRequest))(any(), any())
    }

    "convert a negative balance into a positive amount in pence for the user to pay" in {
      val testPayApiRequestWithAmount =
        SpjRequestBtaSdil("XKSDIL000000033", 1000L, testConfig.sdilHomePage, testConfig.sdilHomePage)

      when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any())).thenReturn {
        Future.successful(Enrolments(Set(Enrolment("HMRC-OBTDS-ORG", Seq(testSdilEnrolment), "Active"))))
      }

      when(mockSdilConnector.balance(any(), any())(any())).thenReturn(Future.successful(BigDecimal(-10)))

      when(mockPayApiConnector.getSdilPayLink(matching(testPayApiRequestWithAmount))(any(), any()))
        .thenReturn(Future.successful(NextUrl(testRedirectUrl)))

      val result = testController.payNow()(FakeRequest())

      status(result) mustBe SEE_OTHER

      verify(mockPayApiConnector).getSdilPayLink(matching(testPayApiRequestWithAmount))(any(), any())
    }
  }
}
