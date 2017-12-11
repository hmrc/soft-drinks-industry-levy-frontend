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
      redirectLocation(result).get mustBe routes.LitreageController.show("packageCopackSmallVol").url
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
      html.select("a.link-back").attr("href") mustBe routes.LitreageController.show("packageCopackSmallVol").url
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

      val html = Jsoup.parse(contentAsString(result))
      html.select("a.link-back").attr("href") mustBe routes.RadioFormController.display(copacked).url
      status(result) mustBe OK
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
