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
import sdil.models.Packaging

class ContactDetailsControllerSpec extends ControllerSpec with BeforeAndAfterEach {

  "Contact details controller" should {
    "return Status: OK for displaying contact details page" in {
      val result = testController.displayContactDetails.apply(FakeRequest())

      status(result) mustBe OK
      contentAsString(result) must include(messagesApi("sdil.contact-details.heading"))
    }

    "return Status: SEE_OTHER for valid contact details form submission" in {
      val request = FakeRequest("POST", "/contact-details").withFormUrlEncodedBody(
        "fullName" -> "hello",
        "position" -> "boss",
        "phoneNumber" -> "+4411111111111",
        "email" -> "a@a.com"
      )
      val result = testController.submitContactDetails.apply(request)

      status(result) mustBe SEE_OTHER
      redirectLocation(result).get mustBe routes.DeclarationController.displayDeclaration().url
    }

    "return Status: BAD_REQUEST for invalid full name for contact details form submission" in {
      val request = FakeRequest("POST", "/contact-details").withFormUrlEncodedBody(
        "fullName" -> "",
        "position" -> "boss",
        "phoneNumber" -> "+4411111111111",
        "email" -> "a@a.com"
      )
      val result = testController.submitContactDetails.apply(request)

      status(result) mustBe BAD_REQUEST
      contentAsString(result) must include(messagesApi("error.full-name.invalid"))
    }

    "return a page with a link back to the import volume page if the user imports liable drinks" in {
      stubFormPage(
        packaging = Some(Packaging(true, true, true),
        packageOwn = None,
        copacked = Some(false),
        copackedVolume = None,
        imports = Some(false),
        importVolume = None
      ))

      val response = controller.displayStartDate(FakeRequest())
      status(response) mustBe OK

      val html = Jsoup.parse(contentAsString(response))
      html.select("a.link-back").attr("href") mustBe routes.LitreageController.show("importVolume").url
    }

  }

  lazy val testController: ContactDetailsController = wire[ContactDetailsController]

  override protected def beforeEach(): Unit = stubFilledInForm
}
