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

import org.mockito.ArgumentMatchers.{eq => matching, _}
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.test.FakeRequest
import play.api.test.Helpers._
import sdil.models.Identification
import uk.gov.hmrc.http.cache.client.{CacheMap, SessionCache}

import scala.concurrent.Future

class IdentifyControllerSpec extends PlayMessagesSpec with MockitoSugar {

  "GET /identify" should {
    "always return 200 Ok and the identify page" in {
      val res = testController.identify()(FakeRequest())
      status(res) mustBe OK
      contentAsString(res) must include("Enter your Unique Tax Reference number and postcode")
    }
  }

  "POST /identify" should {
    "return 400 - Bad Request and the identify page when the form data is invalid" in {
      val request = FakeRequest().withFormUrlEncodedBody("utr" -> "", "postcode" -> "")
      val res = testController.validate()(request)

      status(res) mustBe BAD_REQUEST
      contentAsString(res) must include("Enter your Unique Tax Reference number and postcode")
    }

    "redirect to the verify page if the form data is valid" in {
      val request = FakeRequest().withFormUrlEncodedBody("utr" -> "1122334455", "postcode" -> "AA11 1AA")
      val res = testController.validate()(request)

      status(res) mustBe SEE_OTHER
      redirectLocation(res).value mustBe routes.VerifyController.verify().url
    }

    "store the form data in keystore if it is valid" in {
      val request = FakeRequest().withFormUrlEncodedBody("utr" -> "1234567890", "postcode" -> "AA11 1AA")
      val res = testController.validate()(request)

      status(res) mustBe SEE_OTHER

      verify(controllerhelpers.mockCache, times(1)).cache(matching("identify"), matching(Identification("1234567890", "AA11 1AA")))(any(), any(), any())
    }
  }

  lazy val testController = new IdentifyController(messagesApi) {
    override val cache: SessionCache = controllerhelpers.mockCache
  }

}
