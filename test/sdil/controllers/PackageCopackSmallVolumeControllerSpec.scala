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
import play.api.i18n.Messages
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsString, redirectLocation, status, _}
import sdil.models.Litreage

class PackageCopackSmallVolumeControllerSpec extends ControllerSpec with BeforeAndAfterEach {

  "GET /package-copack-small-vol" should {
    "return 200 Ok and the package copack small vol page if the previous pages have been completed" in {
      val res = testController.show()(FakeRequest())

      status(res) mustBe OK
      contentAsString(res) must include(Messages("sdil.packageCopackSmallVol.heading"))
    }

    "redirect back to the package-copack-small page if it has not been completed" in {
      stubFormPage(packageCopackSmall = None)

      val res = testController.show()(FakeRequest())
      status(res) mustBe SEE_OTHER
      redirectLocation(res) mustBe Some(routes.RadioFormController.display("package-copack-small").url)
    }
  }

  "POST /package-copack-small-vol" should {
    "return 400 Bad Request and the package copack small vol page when the form data is invalid" in {
      val res = testController.validate()(FakeRequest())

      status(res) mustBe BAD_REQUEST
      contentAsString(res) must include(Messages("sdil.packageCopackSmallVol.heading"))
    }

    "redirect to the copacked page if the form data is valid" in {
      val request = FakeRequest().withFormUrlEncodedBody("lowerRateLitres" -> "1", "higherRateLitres" -> "2")
      val res = testController.validate()(request)

      status(res) mustBe SEE_OTHER
      redirectLocation(res) mustBe Some(routes.RadioFormController.display("copacked").url)
    }

    "store the form data in keystore if it is valid" in {
      stubFormPage(packageCopack = Some(Litreage(999, 999)))

      val request = FakeRequest().withFormUrlEncodedBody("lowerRateLitres" -> "4", "higherRateLitres" -> "5")
      val res = testController.validate()(request)

      status(res) mustBe SEE_OTHER
      verifyDataCached(defaultFormData.copy(
        packageCopack = Some(Litreage(999, 999)),
        packageCopackSmallVol = Some(Litreage(4, 5))
      ))
    }
  }

  lazy val testController = wire[PackageCopackSmallVolumeController]

  override protected def beforeEach(): Unit = stubFilledInForm
}
