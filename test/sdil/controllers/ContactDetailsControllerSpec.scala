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

import play.api.test.FakeRequest
import play.api.test.Helpers._

class ContactDetailsControllerSpec extends ControllerSpec {

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

    "return Status: BAD_REQUEST for invalid position for contact details form submission" in {
      val request = FakeRequest("POST", "/contact-details").withFormUrlEncodedBody(
        "fullName" -> "valid",
        "position" -> "",
        "phoneNumber" -> "+4411111111111",
        "email" -> "a@a.com"
      )
      val result = testController.submitContactDetails.apply(request)

      status(result) mustBe BAD_REQUEST
      contentAsString(result) must include(messagesApi("error.position.invalid"))
    }

    "return Status: BAD_REQUEST for invalid phone number for contact details form submission" in {
      val request = FakeRequest("POST", "/contact-details").withFormUrlEncodedBody(
        "fullName" -> "valid",
        "position" -> "position",
        "phoneNumber" -> "",
        "email" -> "a@a.com"
      )
      val result = testController.submitContactDetails.apply(request)

      status(result) mustBe BAD_REQUEST
      contentAsString(result) must include(messagesApi("error.phone-number.invalid"))
    }

    "return Status: BAD_REQUEST for invalid email for contact details form submission" in {
      val request = FakeRequest("POST", "/contact-details").withFormUrlEncodedBody(
        "fullName" -> "valid",
        "position" -> "position",
        "phoneNumber" -> "55561446556",
        "email" -> ""
      )
      val result = testController.submitContactDetails.apply(request)

      status(result) mustBe BAD_REQUEST
      contentAsString(result) must include(messagesApi("error.email"))
    }
  }

  lazy val testController: ContactDetailsController = wire[ContactDetailsController]

}
