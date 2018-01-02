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

import java.time.LocalDate

import org.jsoup.Jsoup
import org.scalatest.BeforeAndAfterEach
import play.api.test.FakeRequest
import play.api.test.Helpers._
import sdil.models.Packaging

class RadioFormControllerSpec extends ControllerSpec with BeforeAndAfterEach {

  "Radio Form Controller" should {
    "return Status: OK when user is logged in and loads package copack small page" in {
      val result = controller.display(copackSmall)(FakeRequest())

      status(result) mustBe OK
      contentAsString(result) must include(messagesApi("sdil.package-copack-small.heading"))
    }

    "return Status: OK when user is logged in and loads copacked page" in {
      val result = controller.display(copacked)(FakeRequest())

      status(result) mustBe OK
      contentAsString(result) must include(messagesApi("sdil.copacked.heading"))
    }

    "return Status: OK when user is logged in and loads import page" in {
      val result = controller.display(imports)(FakeRequest())

      status(result) mustBe OK
      contentAsString(result) must include(messagesApi("sdil.import.heading"))
    }

    "return Status: SEE_OTHER and redirect to package copack small volume with true value for copack small page" in {
      val result = copackSmallSubmit(FakeRequest().withFormUrlEncodedBody(
        "yesOrNo" -> "true"
      ))

      status(result) mustBe SEE_OTHER
      redirectLocation(result).get mustBe routes.PackageCopackSmallVolumeController.show.url
    }

    "return Status: SEE_OTHER and redirect to copacked with false value for copack small page" in {
      val result = copackSmallSubmit(FakeRequest().withFormUrlEncodedBody(
        "yesOrNo" -> "false"
      ))

      status(result) mustBe SEE_OTHER
      routes.RadioFormController.display(copacked).url must include(redirectLocation(result).get)
    }

    "return Status: SEE_OTHER and redirect to copacked volume with true value for copacked page" in {
      val result = copackedSubmit(FakeRequest().withFormUrlEncodedBody(
        "yesOrNo" -> "true"
      ))

      status(result) mustBe SEE_OTHER
      redirectLocation(result).get mustBe routes.LitreageController.show("copackedVolume").url
    }

    "return Status: SEE_OTHER and redirect to import with false value for copacked page" in {
      val result = copackedSubmit(FakeRequest().withFormUrlEncodedBody(
        "yesOrNo" -> "false"
      ))

      status(result) mustBe SEE_OTHER
      routes.RadioFormController.display(imports).url must include(redirectLocation(result).get)
    }

    "return Status: SEE_OTHER and redirect to import volume with true value for import page" in {
      val result = importSubmit(FakeRequest().withFormUrlEncodedBody(
        "yesOrNo" -> "true"
      ))

      status(result) mustBe SEE_OTHER
      redirectLocation(result).get mustBe routes.LitreageController.show("importVolume").url
    }

    "return Status: SEE_OTHER and redirect to the start date page with false value for import page" in {
      val result = importSubmit(FakeRequest().withFormUrlEncodedBody(
        "yesOrNo" -> "false"
      ))

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(routes.StartDateController.displayStartDate().url)
    }

    "return Status: BAD_REQUEST for invalid form input for copacked small form submission" in {
      val result = copackSmallSubmit(FakeRequest().withFormUrlEncodedBody(
        "yesOrNo" -> ""
      ))

      status(result) mustBe BAD_REQUEST
      contentAsString(result) must include(messagesApi("error.radio-form.choose-option.summary"))
    }

    "return Status: BAD_REQUEST for invalid form input for copacked form submission" in {
      val result = copackedSubmit(FakeRequest().withFormUrlEncodedBody(
        "yesOrNo" -> ""
      ))

      status(result) mustBe BAD_REQUEST
      contentAsString(result) must include(messagesApi("error.radio-form.choose-option.summary"))
    }

    "return Status: BAD_REQUEST for invalid form input for import small form submission" in {
      val result = importSubmit.apply(FakeRequest().withFormUrlEncodedBody(
        "yesOrNo" -> ""
      ))

      status(result) mustBe BAD_REQUEST
      contentAsString(result) must include(messagesApi("error.radio-form.choose-option.summary"))
    }

    "generate correct back link for copack small page with false for packaging" in {
      stubFormPage(packaging = Some(Packaging(false, false, false)))

      val result = controller.display(copackSmall)(FakeRequest())

      val html = Jsoup.parse(contentAsString(result))
      html.select("a.link-back").attr("href") mustBe routes.PackageController.displayPackage().url
      status(result) mustBe OK
    }

    "generate correct back link for copack small page with true for packaging and false for customers" in {
      stubFormPage(packaging = Some(Packaging(true, true, false)))
      val result = controller.display(copackSmall)(FakeRequest())

      val html = Jsoup.parse(contentAsString(result))
      html.select("a.link-back").attr("href") mustBe routes.LitreageController.show("packageOwn").url
      status(result) mustBe OK
    }

    "generate correct back link for copack small page with true for packaging and true for customers" in {
      val result = controller.display(copackSmall)(FakeRequest())

      val html = Jsoup.parse(contentAsString(result))
      html.select("a.link-back").attr("href") mustBe routes.LitreageController.show("packageCopack").url
      status(result) mustBe OK
    }

    "generate correct back link for copacked page with true for copack small" in {
      stubFormPage(packageCopackSmall = Some(true))

      val result = controller.display(copacked)(FakeRequest())

      val html = Jsoup.parse(contentAsString(result))
      html.select("a.link-back").attr("href") mustBe routes.PackageCopackSmallVolumeController.show.url
      status(result) mustBe OK
    }

    "generate correct back link for copacked page with false for copack small" in {
      stubFormPage(packageCopackSmall = Some(false))

      val result = controller.display(copacked)(FakeRequest())

      val html = Jsoup.parse(contentAsString(result))
      html.select("a.link-back").attr("href") mustBe routes.RadioFormController.display(copackSmall).url
      status(result) mustBe OK
    }

    "generate correct back link for import page with true for copacked" in {
      stubFormPage(copacked = Some(true))

      val result = controller.display(imports).apply(FakeRequest())

      val html = Jsoup.parse(contentAsString(result))
      html.select("a.link-back").attr("href") mustBe routes.LitreageController.show("copackedVolume").url
      status(result) mustBe OK
    }

    "generate correct back link for copacked page with false for copacked" in {
      stubFormPage(copacked = Some(false))

      val result = controller.display(imports).apply(FakeRequest())

      status(result) mustBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.select("a.link-back").attr("href") mustBe routes.RadioFormController.display(copacked).url
    }

    "redirect to the package page from the copack small page if the package page is not complete" in {
      stubFormPage(packaging = None)

      val res = controller.display(copackSmall)(FakeRequest())
      status(res) mustBe SEE_OTHER
      redirectLocation(res) mustBe Some(routes.PackageController.displayPackage().url)
    }

    "redirect to the package own page from the copack small page if the user packages for their own brand " +
      "and the package own page is not complete" in {
      stubFormPage(packaging = Some(Packaging(true, true, false)), packageOwn = None)

      val res = controller.display(copackSmall)(FakeRequest())
      status(res) mustBe SEE_OTHER
      redirectLocation(res) mustBe Some(routes.LitreageController.show("packageOwn").url)
    }

    "redirect to the package copack page from the copack small page if the user packages for other brands " +
      "and the package copack page is not complete" in {
      stubFormPage(packaging = Some(Packaging(true, true, true)), packageCopack = None)

      val res = controller.display(copackSmall)(FakeRequest())
      status(res) mustBe SEE_OTHER
      redirectLocation(res) mustBe Some(routes.LitreageController.show("packageCopack").url)
    }

    "redirect to the package copack small volume page from the copacked page if the user copacks for small producers " +
      "and the package copack small volume page is not complete" in {
      stubFormPage(packageCopackSmall = Some(true), packageCopackSmallVol = None)

      val res = controller.display(copacked)(FakeRequest())
      status(res) mustBe SEE_OTHER
      redirectLocation(res) mustBe Some(routes.PackageCopackSmallVolumeController.show.url)
    }

    "redirect to the copacked page from the import page if the copacked page is not complete" in {
      stubFormPage(copacked = None)

      val res = controller.display(imports)(FakeRequest())
      status(res) mustBe SEE_OTHER
      redirectLocation(res) mustBe Some(routes.RadioFormController.display(copacked).url)
    }

    "redirect to the copacked volume page from the import page if the user has copackers and the copacked volume page is not complete" in {
      stubFormPage(copacked = Some(true), copackedVolume = None)

      val res = controller.display(imports)(FakeRequest())
      status(res) mustBe SEE_OTHER
      redirectLocation(res) mustBe Some(routes.LitreageController.show("copackedVolume").url)
    }

    "store the form data in keystore, and purge the package copack small volume data, when the package copack small form data is valid" in {
      stubFormPage(startDate = Some(LocalDate.of(2018, 4, 7)))

      val request = FakeRequest().withFormUrlEncodedBody("yesOrNo" -> "false")
      val res = controller.submit(copackSmall)(request)

      status(res) mustBe SEE_OTHER
      verifyDataCached(defaultFormData.copy(
        packageCopackSmall = Some(false),
        packageCopackSmallVol = None,
        startDate = Some(LocalDate.of(2018, 4, 7))
      ))
    }

    "store the form data in keystore, and purge the copacked volume data, when the copacked form data is valid" in {
      stubFormPage(startDate = Some(LocalDate.of(2018, 4, 7)))

      val request = FakeRequest().withFormUrlEncodedBody("yesOrNo" -> "true")
      val res = controller.submit(copacked)(request)

      status(res) mustBe SEE_OTHER
      verifyDataCached(defaultFormData.copy(
        copacked = Some(true),
        copackedVolume = None,
        startDate = Some(LocalDate.of(2018, 4, 7))
      ))
    }

    "store the form data in keystore, and purge the import volume data, when the import form data is valid" in {
      stubFormPage(startDate = Some(LocalDate.of(2018, 4, 7)))

      val request = FakeRequest().withFormUrlEncodedBody("yesOrNo" -> "false")
      val res = controller.submit(imports)(request)

      status(res) mustBe SEE_OTHER
      verifyDataCached(defaultFormData.copy(
        imports = Some(false),
        importVolume = None,
        startDate = Some(LocalDate.of(2018, 4, 7))
      ))
    }
  }

  lazy val controller: RadioFormController = wire[RadioFormController]

  private val copackSmall = "package-copack-small"
  private val copacked = "copacked"
  private val imports = "import"

  private val copackSmallSubmit = controller.submit(copackSmall)
  private val copackedSubmit = controller.submit(copacked)
  private val importSubmit = controller.submit(imports)

  override protected def beforeEach(): Unit = stubFilledInForm
}
