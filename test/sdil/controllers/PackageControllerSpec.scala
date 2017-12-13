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

import org.scalatest.BeforeAndAfterEach
import play.api.test.FakeRequest
import play.api.test.Helpers._
import sdil.models.Packaging

class PackageControllerSpec extends ControllerSpec with BeforeAndAfterEach {

  "Package controller" should {
    "return Status: 200 when user is logged in and loads package page" in {
      val request = FakeRequest("GET", "/package")
      val result = controller.displayPackage().apply(request)

      status(result) mustBe OK
      contentAsString(result) must include(messagesApi("sdil.package.heading"))
    }

    "return Status: See Other for package form POST with isLiable & ownBrands and redirect to package own" in {
      val request = FakeRequest().withFormUrlEncodedBody(
        "isLiable" -> "true",
        "ownBrands" -> "true",
        "customers" -> "true"
      )
      val response = controller.submitPackage().apply(request)

      status(response) mustBe SEE_OTHER
      redirectLocation(response).get mustBe routes.LitreageController.show("packageOwn").url
    }

    "return Status: See Other for package form POST with isLiable & customers and redirect to package copack" in {
      val request = FakeRequest().withFormUrlEncodedBody(
        "isLiable" -> "true",
        "ownBrands" -> "false",
        "customers" -> "true"
      )
      val response = controller.submitPackage().apply(request)

      status(response) mustBe SEE_OTHER
      redirectLocation(response).get mustBe routes.LitreageController.show("packageCopack").url
    }

    "return Status: See Other for package form POST with isLiable false" in {
      val request = FakeRequest().withFormUrlEncodedBody(
        "isLiable" -> "false",
        "ownBrands" -> "false",
        "customers" -> "false"
      )
      val response = controller.submitPackage().apply(request)

      status(response) mustBe SEE_OTHER
      redirectLocation(response).get mustBe routes.RadioFormController.display("copacked").url
    }

    "return Status: Bad Request for invalid liability form POST request and show choose one option error" in {
      val request = FakeRequest().withFormUrlEncodedBody(
        "isLiable" -> "true",
        "customers" -> "false",
        "ownBrands" -> "false"
      )
      val response = controller.submitPackage().apply(request)

      status(response) mustBe BAD_REQUEST
      contentType(response).get mustBe HTML
      contentAsString(response) must include(messagesApi("error.radio-form.choose-one-option"))
    }

    "return Status: Bad Request for invalid liability form POST request and show you have not chosen an option error" in {
      val request = FakeRequest().withFormUrlEncodedBody(
        "isLiable" -> "",
        "customers" -> "false",
        "ownBrands" -> "false"
      )
      val response = controller.submitPackage().apply(request)

      status(response) mustBe BAD_REQUEST
      contentType(response).get mustBe HTML
      contentAsString(response) must include(messagesApi("error.radio-form.choose-option.summary"))
    }

    "return status See Other and redirect to the verify page when the verify page is not complete" in {
      stubFormPage(verify = None)

      val res = controller.displayPackage()(FakeRequest())

      status(res) mustBe SEE_OTHER
      redirectLocation(res) mustBe Some(routes.VerifyController.verify().url)
    }

    "update the keystore record with the form data, and purge the package own, package copack, and production sites data, " +
      "when the form data is valid" in {
      val request = FakeRequest().withFormUrlEncodedBody(
        "isLiable" -> "true",
        "ownBrands" -> "true",
        "customers" -> "false"
      )

      val res = controller.submitPackage()(request)
      status(res) mustBe SEE_OTHER

      verifyDataCached(defaultFormData.copy(
        packaging = Some(Packaging(true, true, false)),
        packageOwn = None,
        packageCopack = None,
        productionSites = None
      ))
    }
  }

  lazy val controller = wire[PackageController]

  override protected def beforeEach(): Unit = stubFilledInForm
}