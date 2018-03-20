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
import play.api.test.FakeRequest
import play.api.test.Helpers._
import sdil.models.Litreage

class ContactDetailsControllerSpec extends ControllerSpec with BeforeAndAfterEach {

  "Contact details controller" should {
    "return Status: OK for displaying contact details page" in {
      val result = testController.show.apply(FakeRequest())

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
      val result = testController.submit.apply(request)

      status(result) mustBe SEE_OTHER
      redirectLocation(result).get mustBe routes.DeclarationController.show().url
    }

    "return Status: BAD_REQUEST for invalid full name for contact details form submission" in {
      val request = FakeRequest("POST", "/contact-details").withFormUrlEncodedBody(
        "fullName" -> "",
        "position" -> "boss",
        "phoneNumber" -> "+4411111111111",
        "email" -> "a@a.com"
      )
      val result = testController.submit.apply(request)

      status(result) mustBe BAD_REQUEST
      contentAsString(result) must include(messagesApi("error.full-name.invalid"))
    }

    "return a page with a link back to the warehouse page if the user is mandatory" in {
      stubFormPage(
        packageOwnVol = Some(Litreage(1000000, 1000000)),
        copacked = Some(true),
        copackedVolume = Some(Litreage(100000, 1000000)),
        imports = Some(true),
        importVolume = Some(Litreage(100000, 1000000))
      )

      val response = testController.show(FakeRequest())
      status(response) mustBe OK

      val html = Jsoup.parse(contentAsString(response))
      html.select("a.link-back").attr("href") mustBe routes.WarehouseController.show().url
    }

    "return a page with a link back to the start date page if the user is voluntary only and it is after the tax start dare" in {
      stubFormPage(
        packageOwnVol = Some(Litreage(1, 2)),
        packageCopackVol = None,
        copacked = Some(true),
        copackedVolume = Some(Litreage(3, 4)),
        imports = Some(false),
        importVolume = None
      )

      val response = testController.show(FakeRequest())
      status(response) mustBe OK

      val html = Jsoup.parse(contentAsString(response))
      html.select("a.link-back").attr("href") mustBe routes.StartDateController.show().url
    }

    "return page with back link to the import page when they are voluntary, it is before the tax start date, " +
      "and they do not import" in {

      testConfig.setTaxStartDate(LocalDate.now plusYears 2)

      stubFormPage(
        packageOwnVol = Some(Litreage(1, 2)),
        packageCopackVol = None,
        copacked = Some(true),
        copackedVolume = Some(Litreage(35, 45)),
        imports = Some(false),
        importVolume = None
      )
      val response = testController.show(FakeRequest())
      status(response) mustBe OK

      val html = Jsoup.parse(contentAsString(response))
      html.select("a.link-back").attr("href") mustBe routes.RadioFormController.show("import").url
    }
  }

  lazy val yesterday: LocalDate = LocalDate.now minusDays 1

  override protected def beforeEach(): Unit = {
    testConfig.setTaxStartDate(yesterday)
    stubFilledInForm
  }

  override protected def afterEach(): Unit = {
    testConfig.resetTaxStartDate()
  }

  lazy val testController: ContactDetailsController = wire[ContactDetailsController]

}
