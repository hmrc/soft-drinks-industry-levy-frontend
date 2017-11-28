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

import org.scalatest.mockito.MockitoSugar
import org.mockito.Mockito._
import org.mockito.ArgumentMatchers.{eq => matching, _}
import play.api.i18n.Messages
import play.api.test.FakeRequest
import uk.gov.hmrc.http.cache.client.{CacheMap, SessionCache}
import play.api.test.Helpers._
import sdil.models.DetailsCorrect.DifferentAddress
import sdil.models.{Address, DetailsCorrect}

import scala.concurrent.Future

class VerifyControllerSpec extends PlayMessagesSpec with MockitoSugar {

  "GET /verify" should {
    "always return 200 Ok and the verify page" in {
      val res = testController.verify()(FakeRequest())

      status(res) mustBe OK
      contentAsString(res) must include (Messages("sdil.verify.heading"))
    }
  }

  "POST /verify" should {
    "return 400 Bad Request and the verify page if the form data is invalid" in {
      val res = testController.validate()(FakeRequest())

      status(res) mustBe BAD_REQUEST
      contentAsString(res) must include (Messages("sdil.verify.heading"))
    }

    "redirect to the package page if the details are correct" in {
      val request = FakeRequest().withFormUrlEncodedBody("detailsCorrect" -> "yes")
      val res = testController.validate()(request)

      status(res) mustBe SEE_OTHER
      redirectLocation(res) mustBe Some(routes.SDILController.displayPackage().url)
    }

    "redirect to the identify page if the details are incorrect" in {
      val request = FakeRequest().withFormUrlEncodedBody("detailsCorrect" -> "no")
      val res = testController.validate()(request)

      status(res) mustBe SEE_OTHER
      redirectLocation(res) mustBe Some(routes.IdentifyController.identify().url)
    }

    "store the form data in keystore if it is valid" in {
      val request = FakeRequest().withFormUrlEncodedBody(
        "detailsCorrect" -> "differentAddress",
        "alternativeAddress.line1" -> "line1",
        "alternativeAddress.line2" -> "line2",
        "alternativeAddress.line3" -> "line3",
        "alternativeAddress.line4" -> "line4",
        "alternativeAddress.postcode" -> "AA11 1AA"
      )

      val res = testController.validate()(request)

      status(res) mustBe SEE_OTHER

      val expectedBody = DifferentAddress(Address("line1", "line2", "line3", "line4", "AA11 1AA"))

      verify(mockCache, times(1))
        .cache(matching("verifiedDetails"), matching(expectedBody))(any(), any(), any())
    }
  }

  lazy val testController = new VerifyController(messagesApi) {
    override val cache: SessionCache = mockCache
  }

  lazy val mockCache = {
    val m = mock[SessionCache]
    when(m.cache(anyString(), any())(any(), any(), any())).thenReturn(Future.successful(CacheMap("id", Map.empty)))
    m
  }
}
