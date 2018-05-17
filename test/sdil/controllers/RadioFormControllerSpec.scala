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

import com.softwaremill.macwire._
import org.jsoup.Jsoup
import org.scalatest.BeforeAndAfterEach
import play.api.i18n.Messages
import play.api.test.FakeRequest
import play.api.test.Helpers._
import sdil.models.{Litreage, Producer}

class RadioFormControllerSpec extends ControllerSpec with BeforeAndAfterEach {

  "Radio Form Controller" should {
    "return Status: OK when user is logged in and loads copacked page" in {
      stubFormPage(
        producer = Some(Producer(isProducer = true, isLarge = Some(false))),
        usesCopacker = None
      )
      val result = controller.show(copacked)(FakeRequest())

      status(result) mustBe OK
      contentAsString(result) must include(messagesApi("sdil.copacked.heading"))
    }

    "return Status: OK when user is logged in and loads import page" in {
      val result = controller.show(imports)(FakeRequest())

      status(result) mustBe OK
      contentAsString(result) must include(messagesApi("sdil.import.heading"))
    }

    "return Status: SEE_OTHER and redirect to the package own uk page if the user uses copackers" in {
      val result = copackedSubmit(FakeRequest().withFormUrlEncodedBody(
        "yesOrNo" -> "true"
      ))

      status(result) mustBe SEE_OTHER
      redirectLocation(result).value mustBe routes.RadioFormController.show("packageOwnUk").url
    }

    "return Status: SEE_OTHER and redirect to the import page if the user does not use copackers" in {
      val result = copackedSubmit(FakeRequest().withFormUrlEncodedBody(
        "yesOrNo" -> "false"
      ))

      status(result) mustBe SEE_OTHER
      redirectLocation(result).value mustBe routes.RadioFormController.show("packageOwnUk").url
    }

    "return Status: BAD_REQUEST for invalid form input for copacked form submission" in {
      val result = copackedSubmit(FakeRequest().withFormUrlEncodedBody(
      "yesOrNo" -> ""
      ))

      status(result) mustBe BAD_REQUEST
      contentAsString(result) must include(messagesApi("sdil.common.errorSummary"))
    }

    "return Status: SEE_OTHER and redirect to the import volume page if the user imports liable drinks" in {
      val result = importSubmit(FakeRequest().withFormUrlEncodedBody(
        "yesOrNo" -> "true"
      ))

      status(result) mustBe SEE_OTHER
      redirectLocation(result).value mustBe routes.LitreageController.show("importVolume").url
    }

    "redirect to the start date page if the user does not import liable drinks and is non-voluntary" in {
      val res = importSubmit(FakeRequest().withFormUrlEncodedBody("yesOrNo" -> "false"))

      status(res) mustBe SEE_OTHER
      redirectLocation(res).value mustBe routes.StartDateController.show().url
    }

    "redirect to the contact details page if the user does not import liable drinks, and is voluntary" in {
      stubFormPage(
        producer = Some(Producer(isProducer = true, isLarge = Some(false))),
        isPackagingForSelf = Some(false),
        packageOwnVol = None,
        usesCopacker = Some(true),
        packagesForOthers = Some(false),
        volumeForCustomerBrands = None,
        imports = Some(false),
        importVolume = None
      )

      val res = importSubmit(FakeRequest().withFormUrlEncodedBody("yesOrNo" -> "false"))

      status(res) mustBe SEE_OTHER
      redirectLocation(res).value mustBe routes.ContactDetailsController.show().url
    }

    "return Status: BAD_REQUEST for invalid form input for import small form submission" in {
      val result = importSubmit.apply(FakeRequest().withFormUrlEncodedBody(
        "yesOrNo" -> ""
      ))

      status(result) mustBe BAD_REQUEST
      contentAsString(result) must include(messagesApi("sdil.common.errorSummary"))
    }

    "generate correct back link for import page with true for packagesForOthers" in {
      stubFormPage(packagesForOthers = Some(true))

      val result = controller.show(imports).apply(FakeRequest())

      val html = Jsoup.parse(contentAsString(result))
      html.select("a.link-back").attr("href") mustBe routes.LitreageController.show("packageCopackVol").url
      status(result) mustBe OK
    }

    "generate correct back link for import page with false for packagesForOthers" in {
      stubFormPage(packagesForOthers = Some(false))

      val result = controller.show(imports).apply(FakeRequest())

      val html = Jsoup.parse(contentAsString(result))
      html.select("a.link-back").attr("href") mustBe routes.RadioFormController.show("packageCopack").url
      status(result) mustBe OK
    }

    "redirect to the copacked page from the import page if the copacked page is not complete" in {
      stubFormPage(
        producer = Some(Producer(false, None)),
        packagesForOthers = None
      )

      val res = controller.show(imports)(FakeRequest())
      status(res) mustBe SEE_OTHER
      redirectLocation(res) mustBe Some(routes.RadioFormController.show("packageCopack").url)
    }

    "return Status: OK when user is logged in and loads packageCopack with producer = No" in {
      stubFormPage(producer = Some(Producer(false, None)))
      val res = controller.show("packageCopack")(FakeRequest())
      status(res) mustBe OK
      contentAsString(res) must include (Messages("sdil.packageCopack.heading"))
    }

    "redirect to the copacked page when user is logged in and loads packageCopack with producer = Yes and " +
      "< 1m litres" in {
      stubFormPage(
        producer = Some(Producer(true, Some(false))),
        usesCopacker = None,
        packagesForOthers = None,
        isPackagingForSelf = None,
        packageOwnVol = None
      )
      val res = controller.show("packageCopack")(FakeRequest())
      status(res) mustBe SEE_OTHER
      redirectLocation(res) mustBe Some(routes.RadioFormController.show("copacked").url)
    }

    "redirect to the copacked page when user is logged in and loads packageCopack with producer = Yes and " +
      "> 1m litres" in {
      stubFormPage(
        producer = Some(Producer(true, Some(true))),
        usesCopacker = None,
        packagesForOthers = None,
        packageOwnVol = None,
        isPackagingForSelf = None
      )
      val res = controller.show("packageCopack")(FakeRequest())
      status(res) mustBe SEE_OTHER
      redirectLocation(res) mustBe Some(routes.RadioFormController.show("packageOwnUk").url)
    }

    "redirect to the copacked volume page from the import page if the user has copackers and the copacked volume page is not complete" in {
      stubFormPage(packagesForOthers = Some(true), volumeForCustomerBrands = None)

      val res = controller.show(imports)(FakeRequest())
      status(res) mustBe SEE_OTHER
      redirectLocation(res) mustBe Some(routes.LitreageController.show("packageCopackVol").url)
    }

    "store the form data and purge the package own volume when the user will not produce their own drinks" in {
      stubFormPage(utr = "3334445554")

      val request = FakeRequest().withFormUrlEncodedBody("yesOrNo" -> "false")
      val res = controller.submit("packageOwnUk")(request)

      status(res) mustBe SEE_OTHER
      verifyDataCached(defaultFormData.copy(
        utr = "3334445554",
        isPackagingForSelf = Some(false),
        volumeForOwnBrand = None
      ))
    }

    "store the form data and not overwrite the package own volume when the user will produce their own drinks" in {
      stubFormPage(utr = "3334445555")

      val request = FakeRequest().withFormUrlEncodedBody("yesOrNo" -> "true")
      val res = controller.submit("packageOwnUk")(request)

      status(res) mustBe SEE_OTHER
      verifyDataCached(defaultFormData.copy(
        utr = "3334445555",
        isPackagingForSelf = Some(true)
      ))
    }

    "store the form data and purge the copacked volume data when the user does not use copackers" in {
      stubFormPage(utr = "3334445556")

      val request = FakeRequest().withFormUrlEncodedBody("yesOrNo" -> "false")
      val res = controller.submit(copacked)(request)

      status(res) mustBe SEE_OTHER
      verifyDataCached(defaultFormData.copy(
        utr = "3334445556",
        usesCopacker = Some(false)
      ))
    }

    "store the form data and purge the import volume data when the user does not import liable drinks" in {
      stubFormPage(utr = "4445556667")

      val request = FakeRequest().withFormUrlEncodedBody("yesOrNo" -> "false")
      val res = controller.submit(imports)(request)

      status(res) mustBe SEE_OTHER
      verifyDataCached(defaultFormData.copy(
        utr = "4445556667",
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

  private val copacked = "copacked"
  private val imports = "import"

  private val copackedSubmit = controller.submit(copacked)
  private val importSubmit = controller.submit(imports)

  override protected def beforeEach(): Unit = stubFilledInForm
}
