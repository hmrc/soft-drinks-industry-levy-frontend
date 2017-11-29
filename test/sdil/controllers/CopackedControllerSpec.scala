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
import org.scalatest.mockito.MockitoSugar
import play.api.test.FakeRequest
import play.api.test.Helpers._
import sdil.controllers.controllerhelpers._

class CopackedControllerSpec extends PlayMessagesSpec with MockitoSugar {

  "GET /copacked" should {
    "always return 200 Ok and the copacked page" in {
      val res = testController.display(FakeRequest())
      status(res) mustBe OK
      contentAsString(res) must include("Will you use any third parties in the UK to package liable drinks on your behalf?")
    }

    "return a page with a back link to the package copack small volume page when the user packages drinks for small producers" in {
      stubCacheEntry[Boolean]("packageCopackSmall", Some(true))

      val res = testController.display(FakeRequest())
      status(res) mustBe OK

      val html = Jsoup.parse(contentAsString(res))
      html.select("a.link-back").attr("href") mustBe routes.LitreageController.show("packageCopackSmallVol").url
    }

    "return a page with a back link to the package copack small page when the user does not package drinks for small producers" in {
      stubCacheEntry[Boolean]("packageCopackSmall", Some(false))

      val res = testController.display(FakeRequest())
      status(res) mustBe OK

      val html = Jsoup.parse(contentAsString(res))
      html.select("a.link-back").attr("href") mustBe routes.PackageCopackSmallController.display().url
    }
  }

  "POST /copacked" should {
    "return 400 - Bad Request and the Copacked page when the form data is invalid" in {
      val request = FakeRequest()
      val res = testController.submit(request)

      status(res) mustBe BAD_REQUEST
      contentAsString(res) must include("You have not chosen an option")
    }

    "redirect to the co-packed-volume page if the form data is true" in {
      val request = FakeRequest().withFormUrlEncodedBody("isCopacked" -> "true")
      val res = testController.submit(request)

      status(res) mustBe SEE_OTHER
      redirectLocation(res).value mustBe routes.LitreageController.show("copackedVolume").url
    }

    "redirect to the start date page if the form data is false" in {
      val request = FakeRequest().withFormUrlEncodedBody("isCopacked" -> "false")
      val res = testController.submit(request)

      status(res) mustBe SEE_OTHER
      redirectLocation(res).value mustBe routes.ImportController.display().url
    }
  }

  lazy val testController = new CopackedController(messagesApi, mockCache)(testConfig)

}
