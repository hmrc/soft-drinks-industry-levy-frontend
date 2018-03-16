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
import org.scalatest.BeforeAndAfterEach
import play.api.test.FakeRequest
import play.api.test.Helpers._
import sdil.models.{Litreage, Packaging}
import com.softwaremill.macwire._

class RadioFormControllerSpec extends ControllerSpec with BeforeAndAfterEach {

  "Radio Form Controller" should {
    "return Status: OK when user is logged in and loads copacked page" in {
      val result = controller.show(copacked)(FakeRequest())

      status(result) mustBe OK
      contentAsString(result) must include(messagesApi("sdil.copacked.heading"))
    }

    "return Status: OK when user is logged in and loads import page" in {
      val result = controller.show(imports)(FakeRequest())

      status(result) mustBe OK
      contentAsString(result) must include(messagesApi("sdil.import.heading"))
    }

    "return Status: SEE_OTHER and redirect to the copacked volume page if the user uses copackers" in {
      val result = copackedSubmit(FakeRequest().withFormUrlEncodedBody(
        "yesOrNo" -> "true"
      ))

      status(result) mustBe SEE_OTHER
      redirectLocation(result).get mustBe routes.LitreageController.show("copackedVolume").url
    }

    "return Status: SEE_OTHER and redirect to the import page if the user does not use copackers" in {
      val result = copackedSubmit(FakeRequest().withFormUrlEncodedBody(
        "yesOrNo" -> "false"
      ))

      status(result) mustBe SEE_OTHER
      routes.RadioFormController.show(imports).url must include(redirectLocation(result).get)
    }

    "return Status: SEE_OTHER and redirect to the import volume page if the user imports liable drinks" in {
      val result = importSubmit(FakeRequest().withFormUrlEncodedBody(
        "yesOrNo" -> "true"
      ))

      status(result) mustBe SEE_OTHER
      redirectLocation(result).get mustBe routes.LitreageController.show("importVolume").url
    }

    "return Status: SEE_OTHER and redirect to the registration type page if the user does not import liable drinks" in {
      val result = importSubmit(FakeRequest().withFormUrlEncodedBody(
        "yesOrNo" -> "false"
      ))

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(routes.RegistrationTypeController.continue().url)
    }

    "return Status: BAD_REQUEST for invalid form input for copacked form submission" in {
      val result = copackedSubmit(FakeRequest().withFormUrlEncodedBody(
        "yesOrNo" -> ""
      ))

      status(result) mustBe BAD_REQUEST
      contentAsString(result) must include(messagesApi("sdil.common.errorSummary"))
    }

    "return Status: BAD_REQUEST for invalid form input for import small form submission" in {
      val result = importSubmit.apply(FakeRequest().withFormUrlEncodedBody(
        "yesOrNo" -> ""
      ))

      status(result) mustBe BAD_REQUEST
      contentAsString(result) must include(messagesApi("sdil.common.errorSummary"))
    }

    "generate correct back link for import page with true for copacked" in {
      stubFormPage(copacked = Some(true))

      val result = controller.show(imports).apply(FakeRequest())

      val html = Jsoup.parse(contentAsString(result))
      html.select("a.link-back").attr("href") mustBe routes.LitreageController.show("copackedVolume").url
      status(result) mustBe OK
    }

    "generate correct back link for copacked page with false for copacked" in {
      stubFormPage(copacked = Some(false))

      val result = controller.show(imports).apply(FakeRequest())

      status(result) mustBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.select("a.link-back").attr("href") mustBe routes.RadioFormController.show(copacked).url
    }

    "redirect to the copacked page from the import page if the copacked page is not complete" in {
      stubFormPage(copacked = None)

      val res = controller.show(imports)(FakeRequest())
      status(res) mustBe SEE_OTHER
      redirectLocation(res) mustBe Some(routes.RadioFormController.show(copacked).url)
    }

    "redirect to the copacked volume page from the import page if the user has copackers and the copacked volume page is not complete" in {
      stubFormPage(copacked = Some(true), copackedVolume = None)

      val res = controller.show(imports)(FakeRequest())
      status(res) mustBe SEE_OTHER
      redirectLocation(res) mustBe Some(routes.LitreageController.show("copackedVolume").url)
    }

    "store the form data and purge the copacked volume data when the user does not use copackers" in {
      stubFormPage(utr = "3334445556")

      val request = FakeRequest().withFormUrlEncodedBody("yesOrNo" -> "false")
      val res = controller.submit(copacked)(request)

      status(res) mustBe SEE_OTHER
      verifyDataCached(defaultFormData.copy(
        utr = "3334445556",
        usesCopacker = Some(false),
        volumeByCopackers = None
      ))
    }

    "store the form data and not overwrite the copacked volume data when the user uses copackers" in {
      stubFormPage(copackedVolume = Some(Litreage(-777, -666)))

      val request = FakeRequest().withFormUrlEncodedBody("yesOrNo" -> "true")
      val res = controller.submit(copacked)(request)

      status(res) mustBe SEE_OTHER
      verifyDataCached(defaultFormData.copy(
        usesCopacker = Some(true),
        volumeByCopackers = Some(Litreage(-777, -666))
      ))
    }

    "store the form data and purge the import volume data when the user does not import liable drinks" in {
      stubFormPage(utr = "4445556667")

      val request = FakeRequest().withFormUrlEncodedBody("yesOrNo" -> "false")
      val res = controller.submit(imports)(request)

      status(res) mustBe SEE_OTHER
      verifyDataCached(defaultFormData.copy(
        isImporter = Some(false),
        importVolume = None
      ))
    }

    "store the form data and not overwrite the import volume data when the user imports liable drinks" in {
      stubFormPage(importVolume = Some(Litreage(-555, -444)))

      val request = FakeRequest().withFormUrlEncodedBody("yesOrNo" -> "true")
      val res = controller.submit(imports)(request)

      status(res) mustBe SEE_OTHER
      verifyDataCached(defaultFormData.copy(
        isImporter = Some(true),
        importVolume = Some(Litreage(-555, -444))
      ))
    }
  }

  lazy val controller: RadioFormController = wire[RadioFormController]

  private val copackSmall = "packageCopackSmall"
  private val copacked = "copacked"
  private val imports = "import"

  private val copackSmallSubmit = controller.submit(copackSmall)
  private val copackedSubmit = controller.submit(copacked)
  private val importSubmit = controller.submit(imports)

  override protected def beforeEach(): Unit = stubFilledInForm
}
