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
import play.api.i18n.Messages
import play.api.test.FakeRequest
import uk.gov.hmrc.http.cache.client.{CacheMap, SessionCache}
import play.api.test.Helpers._
import org.mockito.Mockito._
import org.mockito.ArgumentMatchers.{eq => matching, _}
import org.mockito.verification.VerificationMode
import sdil.models.{Litreage, Packaging}

import scala.concurrent.Future

class LitreageControllerSpec extends PlayMessagesSpec with MockitoSugar {

  "GET /package-own" should {
    "always return 200 Ok and the package own page" in {
      val res = testController.show("packageOwn")(FakeRequest())

      status(res) mustBe OK
      contentAsString(res) must include (Messages("sdil.packageOwn.heading"))
    }
  }

  "POST /package-own" should {
    "return 400 Bad Request and the package own page when the form data is invalid" in {
      val res = testController.validate("packageOwn")(FakeRequest())

      status(res) mustBe BAD_REQUEST
      contentAsString(res) must include (Messages("sdil.packageOwn.heading"))
    }

    "redirect to the package copack page if the form data is valid and the user is packaging for their customers" in {
      when(mockCache.fetchAndGetEntry[Packaging](matching("packaging"))(any(), any(), any()))
        .thenReturn(Future.successful(Some(Packaging(isLiable = false, ownBrands = true, customers = true))))

      val request = FakeRequest().withFormUrlEncodedBody("lowerRateLitres" -> "1", "higherRateLitres" -> "2")
      val res = testController.validate("packageOwn")(request)

      status(res) mustBe SEE_OTHER
      redirectLocation(res) mustBe Some(routes.LitreageController.show("packageCopack").url)
    }

    "redirect to the package copack small page if the form data is valid and the user is not packaging for their customers" in {
      when(mockCache.fetchAndGetEntry[Packaging](matching("packaging"))(any(), any(), any()))
        .thenReturn(Future.successful(Some(Packaging(isLiable = false, ownBrands = true, customers = false))))

      val request = FakeRequest().withFormUrlEncodedBody("lowerRateLitres" -> "2", "higherRateLitres" -> "1")
      val res = testController.validate("packageOwn")(request)

      status(res) mustBe SEE_OTHER
      pending
    }

    "store the form data in keystore if it is valid" in {
      val request = FakeRequest().withFormUrlEncodedBody("lowerRateLitres" -> "2", "higherRateLitres" -> "3")
      val res = testController.validate("packageOwn")(request)

      status(res) mustBe SEE_OTHER
      verify(mockCache, once).cache(matching("packageOwn"), matching(Litreage(2, 3)))(any(), any(), any())
    }
  }

  "GET /package-copack" should {
    "always return 200 Ok and the package copack page" in {
      val res = testController.show("packageCopack")(FakeRequest())

      status(res) mustBe OK
      contentAsString(res) must include (Messages("sdil.packageCopack.heading"))
    }
  }

  "POST /package-copack" should {
    "return 400 Bad Request and the package copack page when the form data is invalid" in {
      val res = testController.validate("packageCopack")(FakeRequest())

      status(res) mustBe BAD_REQUEST
      contentAsString(res) must include (Messages("sdil.packageCopack.heading"))
    }

    "redirect to the package copack small page if the form data is valid" in {
      pending
    }

    "store the form data in keystore if it is valid" in {
      val request = FakeRequest().withFormUrlEncodedBody("lowerRateLitres" -> "3", "higherRateLitres" -> "4")
      val res = testController.validate("packageCopack")(request)

      status(res) mustBe SEE_OTHER
      verify(mockCache, once).cache(matching("packageCopack"), matching(Litreage(3, 4)))(any(), any(), any())
    }
  }

  "GET /package-copack-small-vol" should {
    "always return 200 Ok and the package copack small vol page" in {
      val res = testController.show("packageCopackSmallVol")(FakeRequest())

      status(res) mustBe OK
      contentAsString(res) must include (Messages("sdil.packageCopackSmallVol.heading"))
    }
  }

  "POST /package-copack-small-vol" should {
    "return 400 Bad Request and the package copack small vol page when the form data is invalid" in {
      val res = testController.validate("packageCopackSmallVol")(FakeRequest())

      status(res) mustBe BAD_REQUEST
      contentAsString(res) must include (Messages("sdil.packageCopackSmallVol.heading"))
    }

    "redirect to the copacked page if the form data is valid" in {
      pending
    }

    "store the form data in keystore if it is valid" in {
      val request = FakeRequest().withFormUrlEncodedBody("lowerRateLitres" -> "4", "higherRateLitres" -> "5")
      val res = testController.validate("packageCopackSmallVol")(request)

      status(res) mustBe SEE_OTHER
      verify(mockCache, once).cache(matching("packageCopackSmallVol"), matching(Litreage(4, 5)))(any(), any(), any())
    }
  }

  "GET /copacked-volume" should {
    "always return 200 Ok and the copacked volume page" in {
      val res = testController.show("copackedVolume")(FakeRequest())

      status(res) mustBe OK
      contentAsString(res) must include (Messages("sdil.copackedVolume.heading"))
    }
  }

  "POST /copacked-volume" should {
    "return 400 Bad Request and the copacked volume page when the form data is invalid" in {
      val res = testController.validate("copackedVolume")(FakeRequest())

      status(res) mustBe BAD_REQUEST
      contentAsString(res) must include (Messages("sdil.copackedVolume.heading"))
    }

    "redirect to the import page if the form data is valid" in {
      pending
    }

    "store the form data in keystore if it is valid" in {
      val request = FakeRequest().withFormUrlEncodedBody("lowerRateLitres" -> "5", "higherRateLitres" -> "6")
      val res = testController.validate("packageCopackSmallVol")(request)

      status(res) mustBe SEE_OTHER
      verify(mockCache, once).cache(matching("packageCopackSmallVol"), matching(Litreage(5, 6)))(any(), any(), any())
    }
  }

  "GET /import-volume" should {
    "always return 200 Ok and the import volume page" in {
      val res = testController.show("importVolume")(FakeRequest())

      status(res) mustBe OK
      contentAsString(res) must include (Messages("sdil.importVolume.heading"))
    }
  }

  "POST /import-volume" should {
    "return 400 Bad Request and the import volume page when the form data is invalid" in {
      val res = testController.validate("importVolume")(FakeRequest())

      status(res) mustBe BAD_REQUEST
      contentAsString(res) must include (Messages("sdil.importVolume.heading"))
    }

    "redirect to the start date page if the form data is valid" in {
      val request = FakeRequest().withFormUrlEncodedBody("lowerRateLitres" -> "7", "higherRateLitres" -> "6")
      val res = testController.validate("importVolume")(request)

      status(res) mustBe SEE_OTHER
      redirectLocation(res) mustBe Some(routes.StartDateController.displayStartDate().url)
    }

    "store the form data in keystore if it is valid" in {
      val request = FakeRequest().withFormUrlEncodedBody("lowerRateLitres" -> "6", "higherRateLitres" -> "7")
      val res = testController.validate("importVolume")(request)

      status(res) mustBe SEE_OTHER
      verify(mockCache, once).cache(matching("importVolume"), matching(Litreage(6, 7)))(any(), any(), any())
    }
  }

  lazy val testController = new LitreageController(messagesApi) {
    override val cache: SessionCache = mockCache
  }

  lazy val mockCache = {
    val m = mock[SessionCache]
    when(m.cache(anyString(), any())(any(), any(), any())).thenReturn(Future.successful(CacheMap("id", Map.empty)))
    when(m.fetchAndGetEntry[Packaging](matching("packaging"))(any(), any(), any()))
      .thenReturn(Future.successful(Some(Packaging(false, false, false))))
    m
  }

  lazy val once: VerificationMode = times(1)
}
