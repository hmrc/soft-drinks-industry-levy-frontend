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
import org.mockito.ArgumentMatchers.{eq => matching}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import sdil.models.Packaging

class PackageCopackSmallControllerSpec extends ControllerSpec {

  "GET /package-copack-small" should {
    "always return 200 Ok and the package-copack-small page" in {
      val res = testController.display(FakeRequest())
      status(res) mustBe OK
      contentAsString(res) must include("Will you package liable drinks for any small producers?")
    }

    "return a page with a link back to the package copack page if the user packages liable drinks for customers" in {
      stubCacheEntry[Packaging]("packaging", Some(Packaging(true, true, true)))

      val res = testController.display()(FakeRequest())
      status(res) mustBe OK

      val html = Jsoup.parse(contentAsString(res))
      html.select("a.link-back").attr("href") mustBe routes.LitreageController.show("packageCopack").url
    }

    "return a page with a link to the package own page if the user packages drinks for their own brand, but not customers" in {
      stubCacheEntry[Packaging]("packaging", Some(Packaging(true, true, false)))

      val res = testController.display()(FakeRequest())
      status(res) mustBe OK

      val html = Jsoup.parse(contentAsString(res))
      html.select("a.link-back").attr("href") mustBe routes.LitreageController.show("packageOwn").url
    }

    "return a page with a link to the package page if the user does not package liable drinks" in {
      stubCacheEntry[Packaging]("packaging", Some(Packaging(false, false, false)))

      val res = testController.display()(FakeRequest())
      status(res) mustBe OK

      val html = Jsoup.parse(contentAsString(res))
      html.select("a.link-back").attr("href") mustBe routes.PackageController.displayPackage().url
    }
  }

  "POST /package-copack-small" should {
    "return 400 - Bad Request and the package-copack-small page when the form data is invalid" in {
      val request = FakeRequest()
      val res = testController.submit(request)

      status(res) mustBe BAD_REQUEST
      contentAsString(res) must include("You have not chosen an option")
    }

    "redirect to the co-packed-volume page if the form data is true" in {
      val request = FakeRequest().withFormUrlEncodedBody("isPackageCopackSmall" -> "true")
      val res = testController.submit(request)

      status(res) mustBe SEE_OTHER
      redirectLocation(res).value mustBe routes.LitreageController.show("packageCopackSmallVol").url
    }

    "redirect to the start date page if the form data is false" in {
      val request = FakeRequest().withFormUrlEncodedBody("isPackageCopackSmall" -> "false")
      val res = testController.submit(request)

      status(res) mustBe SEE_OTHER
      redirectLocation(res).value mustBe routes.CopackedController.display().url
    }
  }

  lazy val testController = wire[PackageCopackSmallController]
}
