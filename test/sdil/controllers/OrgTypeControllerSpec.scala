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

import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsString, status, _}
import play.i18n.MessagesApi

class OrgTypeControllerSpec extends ControllerSpec with BeforeAndAfterEach {
  "OrgType controller" should {
    "always return 200 Ok and the organisation type page" in {
      val request = FakeRequest("GET", "/organisation-type")
      val response = testController.displayOrgType().apply(request)
      status(response) mustBe OK
      contentAsString(response) must include(messagesApi("sdil.organisation-type.heading"))
    }

    "return Status: Bad Request for invalid organisation form POST request and show choose option error" in {
      val request = FakeRequest().withFormUrlEncodedBody(
        "orgType" -> "badCompany")
      val response = testController.submitOrgType().apply(request)

      status(response) mustBe BAD_REQUEST
      contentType(response).get mustBe HTML
      contentAsString(response) must include(messagesApi("error.radio-form.choose-option"))
    }

    "return Status: See Other for valid organisation form POST request and redirect to packaging page" in {
      val request = FakeRequest().withFormUrlEncodedBody(
        "orgType" -> "soleTrader")
      val response = testController.submitOrgType().apply(request)

      status(response) mustBe SEE_OTHER
      redirectLocation(response).get mustBe routes.PackageController.displayPackage().url
    }
    lazy val testController = wire[OrgTypeController]
  }
  override protected def beforeEach(): Unit = stubFilledInForm

}

