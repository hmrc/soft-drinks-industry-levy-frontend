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

import com.softwaremill.macwire.wire
import org.jsoup.Jsoup
import org.scalatest.BeforeAndAfterEach
import play.api.i18n.Messages
import play.api.test.FakeRequest
import play.api.test.Helpers.{OK, contentAsString, status, _}
import sdil.models.Producer

class ProducerControllerSpec extends ControllerSpec with BeforeAndAfterEach {

  "/producer" should {
    "always return 200 Ok and the producer page " in {
      val res = testController.show()(FakeRequest())

      status(res) mustBe OK
      contentAsString(res) must include (Messages("sdil.producer.heading"))
    }

    "return Status: Bad Request for invalid producer form POST request and show choose option error" in {
      val res = testController.submit()(FakeRequest())

      status(res) mustBe BAD_REQUEST
      contentAsString(res) must include (Messages("error.radio-form.choose-option"))
    }

    "redirect to the package copack page if the user is not a producer" in {
      val res = testController.submit()(FakeRequest().withFormUrlEncodedBody("isProducer" -> "false"))

      status(res) mustBe SEE_OTHER
      redirectLocation(res).value mustBe routes.RadioFormController.show("packageCopack").url
    }

    "return the copacked page for Yes and < 1m " in {
      val res = testController.submit()(FakeRequest()
        .withFormUrlEncodedBody("isProducer" -> "true", "isLarge" -> "false"))

      status(res) mustBe SEE_OTHER
      redirectLocation(res).value mustBe routes.RadioFormController.show("copacked").url
    }

    "return the package-own page for Yes and > 1m " in {
      val res = testController.submit()(FakeRequest()
        .withFormUrlEncodedBody("isProducer" -> "true", "isLarge" -> "true"))

      status(res) mustBe SEE_OTHER
      redirectLocation(res).value mustBe routes.RadioFormController.show("packageOwnUk").url
    }

    "save the form data, and purge the copacked data, when the user is not a producer" in {
      stubFormPage(utr = "9998887776")

      val res = testController.submit()(FakeRequest().withFormUrlEncodedBody("isProducer" -> "false"))

      status(res) mustBe SEE_OTHER
      verifyDataCached(defaultFormData.copy(
        utr = "9998887776",
        producer = Some(Producer(isProducer = false, isLarge = None)),
        usesCopacker = None,
        isPackagingForSelf = None,
        volumeForOwnBrand = None
      ))
    }

    "save the form data, and purge the copacked data, when the user produced more than 1 million litres" in {
      stubFormPage(utr = "9998887775")

      val res = testController.submit()(FakeRequest()
        .withFormUrlEncodedBody("isProducer" -> "true", "isLarge" -> "true")
      )

      status(res) mustBe SEE_OTHER
      verifyDataCached(defaultFormData.copy(
        utr = "9998887775",
        producer = Some(Producer(isProducer = true, isLarge = Some(true))),
        usesCopacker = None
      ))
    }

    "save the form data when the user produced less than 1 million litres" in {
      stubFormPage(utr = "9998887774")

      val res = testController.submit()(FakeRequest()
        .withFormUrlEncodedBody("isProducer" -> "true", "isLarge" -> "false")
      )

      status(res) mustBe SEE_OTHER
      verifyDataCached(defaultFormData.copy(
        utr = "9998887774",
        producer = Some(Producer(isProducer = true, isLarge = Some(false)))
      ))
    }

    "return a page with a link back to the organisation type page" in {
      val res = testController.show()(FakeRequest())
      status(res) mustBe OK

      val html = Jsoup.parse(contentAsString(res))
      html.select("a.link-back").attr("href") mustBe routes.OrganisationTypeController.show().url
    }
  }

  lazy val testController = wire[ProducerController]

  override protected def beforeEach(): Unit = stubFilledInForm

}
