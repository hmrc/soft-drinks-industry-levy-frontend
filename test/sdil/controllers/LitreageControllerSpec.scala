/*
 * Copyright 2018 HM Revenue & Customs
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

import org.jsoup.Jsoup
import org.mockito.ArgumentMatchers.{any, eq => matching}
import org.mockito.Mockito._
import org.mockito.verification.VerificationMode
import org.scalatest.BeforeAndAfterEach
import play.api.i18n.Messages
import play.api.test.FakeRequest
import play.api.test.Helpers._
import sdil.models.{Litreage, Packaging, Producer}

class LitreageControllerSpec extends ControllerSpec with BeforeAndAfterEach {

  "GET /package-own-vol" should {
    "return 200 Ok and the package own page if the package page has been completed" in {
      stubFormPage(
        producer = Some(Producer(true, Some(true))),
        isPackagingForSelf = Some(true)
      )
      val res = testController.show("packageOwnVol")(FakeRequest())

      status(res) mustBe OK
      contentAsString(res) must include(Messages("sdil.packageOwnVol.heading"))
    }

    "redirect back to the producer page if it has not been completed" in {
      stubFormPage(producer = None)

      val res = testController.show("packageOwnVol")(FakeRequest())
      status(res) mustBe SEE_OTHER
      redirectLocation(res) mustBe Some(routes.ProducerController.show().url)
    }
  }

  "POST /package-own" should {
    "return 400 Bad Request and the package own page when the form data is invalid" in {
      val res = testController.submit("packageOwnVol")(FakeRequest())

      status(res) mustBe BAD_REQUEST
      contentAsString(res) must include(Messages("sdil.packageOwnVol.heading"))
    }

    "redirect to the packages for others page if the form data is valid" in {
      val request = FakeRequest().withFormUrlEncodedBody("lowerRateLitres" -> "1", "higherRateLitres" -> "2")
      val res = testController.submit("packageOwnVol")(request)

      status(res) mustBe SEE_OTHER
      redirectLocation(res).value mustBe routes.RadioFormController.show("packageCopack").url
    }

    "store the form data in keystore if it is valid" in {
      val request = FakeRequest().withFormUrlEncodedBody("lowerRateLitres" -> "2", "higherRateLitres" -> "3")
      val res = testController.submit("packageOwnVol")(request)

      status(res) mustBe SEE_OTHER
      verify(mockCache, once).cache(
        matching("internal id"),
        matching(defaultFormData.copy(volumeForOwnBrand = Some(Litreage(2, 3))))
      )(any())
    }
  }

  "GET /package-copack-vol" should {
    "return 200 Ok and the package copack page if the previous pages have been completed" in {
      val res = testController.show("packageCopackVol")(FakeRequest())

      status(res) mustBe OK
      contentAsString(res) must include(Messages("sdil.packageCopackVol.heading"))
    }
//TODO Change when routing has been finalised
//    "redirect back to the package copack page if it has not been completed" in {
//      stubFormPage(packagesForOthers = None)
//
//      val res = testController.show("packageCopackVol")(FakeRequest())
//      status(res) mustBe SEE_OTHER
//      redirectLocation(res) mustBe Some(routes.RadioFormController.show("packageCopack").url)
//    }

    "return a page with a link back to the package copack page" in {
      val res = testController.show("packageCopackVol")(FakeRequest())
      status(res) mustBe OK

      val html = Jsoup.parse(contentAsString(res))
      val backLink = html.select("a.link-back")
      backLink.attr("href") mustBe routes.RadioFormController.show("packageCopack").url
    }
  }

  "POST /package-copack" should {
    "return 400 Bad Request and the package copack page when the form data is invalid" in {
      val res = testController.submit("packageCopackVol")(FakeRequest())

      status(res) mustBe BAD_REQUEST
      contentAsString(res) must include(Messages("sdil.packageCopackVol.heading"))
    }

    "redirect to the package copack small page if the form data is valid" in {
      val request = FakeRequest().withFormUrlEncodedBody("lowerRateLitres" -> "1", "higherRateLitres" -> "2")

      val res = testController.submit("packageCopackVol")(request)
      status(res) mustBe SEE_OTHER
      redirectLocation(res) mustBe Some(routes.RadioFormController.show("packageCopackSmall").url)
    }

    "store the form data in keystore if it is valid" in {
      val request = FakeRequest().withFormUrlEncodedBody("lowerRateLitres" -> "4", "higherRateLitres" -> "3")
      val res = testController.submit("packageCopackVol")(request)

      status(res) mustBe SEE_OTHER
      verify(mockCache, once).cache(
        matching("internal id"),
        matching(defaultFormData.copy(volumeForCustomerBrands = Some(Litreage(4, 3))))
      )(any())
    }
  }

  "GET /copacked-volume" should {
    "return 200 Ok and the copacked volume page if the previous pages have been completed" in {
      val res = testController.show("copackedVolume")(FakeRequest())

      status(res) mustBe OK
      contentAsString(res) must include(Messages("sdil.copackedVolume.heading"))
    }

    "redirect back to the copacked page if it has not been completed" in {
      stubFormPage(
        producer = Some(Producer(false, None)),
        copacked = None)

      val res = testController.show("copackedVolume")(FakeRequest())
      status(res) mustBe SEE_OTHER
      redirectLocation(res) mustBe Some(routes.RadioFormController.show("copacked").url)
    }
  }

  "POST /copacked-volume" should {
    "return 400 Bad Request and the copacked volume page when the form data is invalid" in {
      val res = testController.submit("copackedVolume")(FakeRequest())

      status(res) mustBe BAD_REQUEST
      contentAsString(res) must include(Messages("sdil.copackedVolume.heading"))
    }

    "redirect to the import page if the form data is valid" in {
      val request = FakeRequest().withFormUrlEncodedBody("lowerRateLitres" -> "1", "higherRateLitres" -> "2")
      val res = testController.submit("copackedVolume")(request)

      status(res) mustBe SEE_OTHER
      redirectLocation(res) mustBe Some(routes.RadioFormController.show("import").url)
    }

    "store the form data in keystore if it is valid" in {
      val request = FakeRequest().withFormUrlEncodedBody("lowerRateLitres" -> "5", "higherRateLitres" -> "6")
      val res = testController.submit("copackedVolume")(request)

      status(res) mustBe SEE_OTHER
      verify(mockCache, once).cache(
        matching("internal id"),
        matching(defaultFormData.copy(volumeByCopackers = Some(Litreage(5, 6))))
      )(any())
    }
  }

  "GET /import-volume" should {
    "return 200 Ok and the import volume page if the import page has been completed" in {
      val res = testController.show("importVolume")(FakeRequest())

      status(res) mustBe OK
      contentAsString(res) must include(Messages("sdil.importVolume.heading"))
    }

    "redirect back to the import page if it has not been completed" in {
      stubFormPage(imports = None)

      val res = testController.show("importVolume")(FakeRequest())
      status(res) mustBe SEE_OTHER
      redirectLocation(res) mustBe Some(routes.RadioFormController.show("import").url)
    }
  }

  "POST /import-volume" should {
    "return 400 Bad Request and the import volume page when the form data is invalid" in {
      val res = testController.submit("importVolume")(FakeRequest())

      status(res) mustBe BAD_REQUEST
      contentAsString(res) must include(Messages("sdil.importVolume.heading"))
    }

    "redirect to the registration type controller if the form data is valid" in {
      val request = FakeRequest().withFormUrlEncodedBody("lowerRateLitres" -> "7", "higherRateLitres" -> "6")
      val res = testController.submit("importVolume")(request)

      status(res) mustBe SEE_OTHER
      redirectLocation(res) mustBe Some(routes.RegistrationTypeController.continue().url)
    }

    "store the form data in keystore if it is valid" in {
      val request = FakeRequest().withFormUrlEncodedBody("lowerRateLitres" -> "6", "higherRateLitres" -> "7")
      val res = testController.submit("importVolume")(request)

      status(res) mustBe SEE_OTHER
      verify(mockCache, once).cache(
        matching("internal id"),
        matching(defaultFormData.copy(importVolume = Some(Litreage(6, 7))))
      )(any())
    }
  }

  lazy val testController = wire[LitreageController]

  lazy val once: VerificationMode = times(1)

  override protected def beforeEach(): Unit = stubFilledInForm
}
