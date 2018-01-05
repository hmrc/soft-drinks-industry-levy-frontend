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
import sdil.models.{Litreage, Packaging}

class LitreageControllerSpec extends ControllerSpec with BeforeAndAfterEach {

  "GET /package-own" should {
    "return 200 Ok and the package own page if the package page has been completed" in {
      val res = testController.show("packageOwn")(FakeRequest())

      status(res) mustBe OK
      contentAsString(res) must include(Messages("sdil.packageOwn.heading"))
    }

    "redirect back to the package page if it has not been completed" in {
      stubFormPage(packaging = None)

      val res = testController.show("packageOwn")(FakeRequest())
      status(res) mustBe SEE_OTHER
      redirectLocation(res) mustBe Some(routes.PackageController.displayPackage().url)
    }
  }

  "POST /package-own" should {
    "return 400 Bad Request and the package own page when the form data is invalid" in {
      val res = testController.validate("packageOwn")(FakeRequest())

      status(res) mustBe BAD_REQUEST
      contentAsString(res) must include(Messages("sdil.packageOwn.heading"))
    }

    "redirect to the copacked volume page if the form data is valid and the user is packaging for their customers" in {
      val request = FakeRequest().withFormUrlEncodedBody("lowerRateLitres" -> "1", "higherRateLitres" -> "2")
      val res = testController.validate("packageOwn")(request)

      status(res) mustBe SEE_OTHER
      redirectLocation(res) mustBe Some(routes.LitreageController.show("packageCopack").url)
    }

    "redirect to the package copack page if the form data is valid and the user is not packaging for their customers" in {
      stubFormPage(packaging = Some(Packaging(false, false, false)))

      val request = FakeRequest().withFormUrlEncodedBody("lowerRateLitres" -> "2", "higherRateLitres" -> "1")
      val res = testController.validate("packageOwn")(request)

      status(res) mustBe SEE_OTHER
      redirectLocation(res) mustBe Some(routes.RadioFormController.display("copacked").url)
    }

    "store the form data in keystore if it is valid" in {
      val request = FakeRequest().withFormUrlEncodedBody("lowerRateLitres" -> "2", "higherRateLitres" -> "3")
      val res = testController.validate("packageOwn")(request)

      status(res) mustBe SEE_OTHER
      verify(mockCache, once).cache(
        matching("formData"),
        matching(defaultFormData.copy(packageOwn = Some(Litreage(2, 3))))
      )(any(), any(), any())
    }
  }

  "GET /package-copack" should {
    "return 200 Ok and the package copack page if the previous pages have been completed" in {
      val res = testController.show("packageCopack")(FakeRequest())

      status(res) mustBe OK
      contentAsString(res) must include(Messages("sdil.packageCopack.heading"))
    }

    "redirect back to the package page if it has not been completed" in {
      stubFormPage(packaging = None)

      val res = testController.show("packageCopack")(FakeRequest())
      status(res) mustBe SEE_OTHER
      redirectLocation(res) mustBe Some(routes.PackageController.displayPackage().url)
    }

    "redirect back to the package-own page if the user packages their own drinks and the package-own page has been skipped" in {
      stubFormPage(packaging = Some(Packaging(true, true, true)), packageOwn = None)

      val res = testController.show("packageCopack")(FakeRequest())
      status(res) mustBe SEE_OTHER
      redirectLocation(res) mustBe Some(routes.LitreageController.show("packageOwn").url)
    }

    "return a page with a link back to the package own page if the user packages liable drinks" in {
      val res = testController.show("packageCopack")(FakeRequest())
      status(res) mustBe OK

      val html = Jsoup.parse(contentAsString(res))
      html.select("a.link-back").attr("href") mustBe routes.LitreageController.show("packageOwn").url
    }

    "return a page with a link back to the package page if the user does not package liable drinks" in {
      stubFormPage(packaging = Some(Packaging(false, false, false)))

      val res = testController.show("packageCopack")(FakeRequest())
      status(res) mustBe OK

      val html = Jsoup.parse(contentAsString(res))
      html.select("a.link-back").attr("href") mustBe routes.PackageController.displayPackage().url
    }
  }

  "POST /package-copack" should {
    "return 400 Bad Request and the package copack page when the form data is invalid" in {
      val res = testController.validate("packageCopack")(FakeRequest())

      status(res) mustBe BAD_REQUEST
      contentAsString(res) must include(Messages("sdil.packageCopack.heading"))
    }

    "redirect to the package copack small page if the form data is valid" in {
      val request = FakeRequest().withFormUrlEncodedBody("lowerRateLitres" -> "1", "higherRateLitres" -> "2")

      val res = testController.validate("packageCopack")(request)
      status(res) mustBe SEE_OTHER
      redirectLocation(res) mustBe Some(routes.RadioFormController.display("package-copack-small").url)
    }

    "store the form data in keystore if it is valid" in {
      val request = FakeRequest().withFormUrlEncodedBody("lowerRateLitres" -> "4", "higherRateLitres" -> "3")
      val res = testController.validate("packageCopack")(request)

      status(res) mustBe SEE_OTHER
      verify(mockCache, once).cache(
        matching("formData"),
        matching(defaultFormData.copy(packageCopack = Some(Litreage(4, 3))))
      )(any(), any(), any())
    }
  }

  "GET /copacked-volume" should {
    "return 200 Ok and the copacked volume page if the previous pages have been completed" in {
      val res = testController.show("copackedVolume")(FakeRequest())

      status(res) mustBe OK
      contentAsString(res) must include(Messages("sdil.copackedVolume.heading"))
    }

    "redirect back to the copacked page if it has not been completed" in {
      stubFormPage(copacked = None)

      val res = testController.show("copackedVolume")(FakeRequest())
      status(res) mustBe SEE_OTHER
      redirectLocation(res) mustBe Some(routes.RadioFormController.display("copacked").url)
    }
  }

  "POST /copacked-volume" should {
    "return 400 Bad Request and the copacked volume page when the form data is invalid" in {
      val res = testController.validate("copackedVolume")(FakeRequest())

      status(res) mustBe BAD_REQUEST
      contentAsString(res) must include(Messages("sdil.copackedVolume.heading"))
    }

    "redirect to the import page if the form data is valid" in {
      val request = FakeRequest().withFormUrlEncodedBody("lowerRateLitres" -> "1", "higherRateLitres" -> "2")
      val res = testController.validate("copackedVolume")(request)

      status(res) mustBe SEE_OTHER
      redirectLocation(res) mustBe Some(routes.RadioFormController.display("import").url)
    }

    "store the form data in keystore if it is valid" in {
      val request = FakeRequest().withFormUrlEncodedBody("lowerRateLitres" -> "5", "higherRateLitres" -> "6")
      val res = testController.validate("copackedVolume")(request)

      status(res) mustBe SEE_OTHER
      verify(mockCache, once).cache(
        matching("formData"),
        matching(defaultFormData.copy(copackedVolume = Some(Litreage(5, 6))))
      )(any(), any(), any())
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
      redirectLocation(res) mustBe Some(routes.RadioFormController.display("import").url)
    }
  }

  "POST /import-volume" should {
    "return 400 Bad Request and the import volume page when the form data is invalid" in {
      val res = testController.validate("importVolume")(FakeRequest())

      status(res) mustBe BAD_REQUEST
      contentAsString(res) must include(Messages("sdil.importVolume.heading"))
    }

    "redirect to the registration type controller if the form data is valid" in {
      val request = FakeRequest().withFormUrlEncodedBody("lowerRateLitres" -> "7", "higherRateLitres" -> "6")
      val res = testController.validate("importVolume")(request)

      status(res) mustBe SEE_OTHER
      redirectLocation(res) mustBe Some(routes.RegistrationTypeController.continue().url)
    }

    "store the form data in keystore if it is valid" in {
      val request = FakeRequest().withFormUrlEncodedBody("lowerRateLitres" -> "6", "higherRateLitres" -> "7")
      val res = testController.validate("importVolume")(request)

      status(res) mustBe SEE_OTHER
      verify(mockCache, once).cache(
        matching("formData"),
        matching(defaultFormData.copy(importVolume = Some(Litreage(6, 7))))
      )(any(), any(), any())
    }
  }

  lazy val testController = wire[LitreageController]

  lazy val once: VerificationMode = times(1)

  override protected def beforeEach(): Unit = stubFilledInForm
}
