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
import uk.gov.hmrc.http.cache.client.{CacheMap, SessionCache}

import scala.concurrent.Future

class CopackedControllerSpec extends PlayMessagesSpec with MockitoSugar {

  "GET /copacked" should {
    "always return 200 Ok and the copacked page" in {
      val res = testController.display(FakeRequest())
      status(res) mustBe OK
      contentAsString(res) must include("Will you use any third parties in the UK to package liable drinks on your behalf?")
    }
  }

  "POST /copacked" should {
    "return 400 - Bad Request and the Copacked page when the form data is invalid" in {
      val request = FakeRequest()
      val res = testController.submit(request)

      status(res) mustBe BAD_REQUEST
      contentAsString(res) must include("You have not chosen an option")
    }
    "redirect to the co-packed-volume page if the form data is true" in {
      {
        val request = FakeRequest().withFormUrlEncodedBody("isCopacked" -> "true")
        val res = testController.submit(request)

        status(res) mustBe SEE_OTHER
        redirectLocation(res).value mustBe routes.LitreageController.show("copackedVolume").url
      }
    }
    "redirect to the start date page if the form data is false" in {
      {
        val request = FakeRequest().withFormUrlEncodedBody("isCopacked" -> "false")
        val res = testController.submit(request)

        status(res) mustBe SEE_OTHER
        redirectLocation(res).value mustBe routes.ImportController.display().url
      }
    }
  }
  lazy val testController = new CopackedController(messagesApi) {
    override val cache: SessionCache = mockCache
  }

  lazy val mockCache = {
    val m = mock[SessionCache]
    when(m.cache(anyString(), any())(any(), any(), any())).thenReturn(Future.successful(CacheMap("", Map.empty)))
    m
  }
}
