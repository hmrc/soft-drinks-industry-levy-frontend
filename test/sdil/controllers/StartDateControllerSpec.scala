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

import org.jsoup.Jsoup
import org.scalatest.BeforeAndAfterEach
import play.api.test.FakeRequest
import play.api.test.Helpers._
import sdil.models.Packaging

class StartDateControllerSpec extends ControllerSpec with BeforeAndAfterEach {

  val controller = wire[StartDateController]

  "StartDateController" should {
    "return Status: 200 when user is logged in and loads start date page" in {
      val request = FakeRequest("GET", "/start-date")
      val result = controller.displayStartDate.apply(request)

      status(result) mustBe OK
      contentAsString(result) must include(messagesApi("sdil.start-date.heading"))
    }

    "return Status: See Other for start date form POST with valid date and redirect to add site page" in {
      stubFormPage(packaging = Some(packagingIsLiable))

      val request = FakeRequest().withFormUrlEncodedBody(validStartDateForm: _*)

      val response = controller.submitStartDate()(request)

      status(response) mustBe SEE_OTHER
      redirectLocation(response).get mustBe routes.ProductionSiteController.addSite().url
    }

    "return Status: See Other for start date form POST with valid date and redirect to secondary warehouse page" in {
      stubFormPage(packaging = Some(packagingIsntLiable))

      val request = FakeRequest().withFormUrlEncodedBody(validStartDateForm: _*)
      val response = controller.submitStartDate().apply(request)

      status(response) mustBe SEE_OTHER
      redirectLocation(response).get mustBe routes.WarehouseController.show().url
    }

    "return Status: Bad Request for invalid start date form POST request with day too low and display field hint" in {
      val request = FakeRequest().withFormUrlEncodedBody(
        "startDate.day" -> "-2",
        "startDate.month" -> "08",
        "startDate.year" -> "2017"
      )
      val response = controller.submitStartDate().apply(request)

      status(response) mustBe BAD_REQUEST
      contentType(response).get mustBe HTML
      contentAsString(response) must include(messagesApi("error.start-day.invalid"))
    }

    "return a page with a link back to the import volume page if the user imports liable drinks" in {
      stubFormPage(imports = Some(true))

      val response = controller.displayStartDate(FakeRequest())
      status(response) mustBe OK

      val html = Jsoup.parse(contentAsString(response))
      html.select("a.link-back").attr("href") mustBe routes.LitreageController.show("importVolume").url
    }

    "return a page with a link back to the imports page if the user does not import liable drinks" in {
      stubFormPage(imports = Some(false))

      val response = controller.displayStartDate(FakeRequest())
      status(response) mustBe OK

      val html = Jsoup.parse(contentAsString(response))
      html.select("a.link-back").attr("href") mustBe routes.RadioFormController.display("import").url
    }

    "return Status: See Other for start date form GET with valid date and Liable booleans with redirect to add site page" in {
      stubFormPage(packaging = Some(packagingIsLiable))
      testConfig.setTaxStartDate(tomorrow)

      val request = FakeRequest().withFormUrlEncodedBody(validStartDateForm: _*)

      val response = controller.displayStartDate().apply(request)
      status(response) mustBe SEE_OTHER
      redirectLocation(response).get mustBe routes.ProductionSiteController.addSite().url
    }

    "return Status: See Other for start date form GET with valid date and no Liable booleans with redirect to display Package page" in {
      stubFormPage(packaging = None)
      testConfig.setTaxStartDate(tomorrow)

      val request = FakeRequest().withFormUrlEncodedBody(validStartDateForm: _*)

      val response = controller.displayStartDate().apply(request)
      status(response) mustBe SEE_OTHER
      redirectLocation(response).get mustBe routes.PackageController.displayPackage().url
    }

    "return Status: See Other for start date form GET with valid date and isnt Liable booleans with redirect to secondary warehouse page" in {
      stubFormPage(packaging = Some(packagingIsntLiable))
      testConfig.setTaxStartDate(tomorrow)

      val request = FakeRequest().withFormUrlEncodedBody(validStartDateForm: _*)

      val response = controller.displayStartDate().apply(request)
      status(response) mustBe SEE_OTHER
      redirectLocation(response).get mustBe routes.WarehouseController.show().url
    }

    "return status See Other and redirect to the import page if the import page is not complete" in {
      stubFormPage(imports = None)

      val res = controller.displayStartDate()(FakeRequest())
      status(res) mustBe SEE_OTHER
      redirectLocation(res) mustBe Some(routes.RadioFormController.display("import").url)
    }

    "return status See Other and redirect to the import volume page if the user imports and the import volume page is not complete" in {
      stubFormPage(imports = Some(true), importVolume = None)

      val res = controller.displayStartDate()(FakeRequest())
      status(res) mustBe SEE_OTHER
      redirectLocation(res) mustBe Some(routes.LitreageController.show("importVolume").url)
    }
  }

  private lazy val packagingIsLiable = Packaging(true, true, false)
  private lazy val packagingIsntLiable = Packaging(false, false, false)

  lazy val tomorrow = LocalDate.now plusDays 1
  lazy val yesterday: LocalDate = LocalDate.now minusDays 1

  override protected def beforeEach(): Unit = {
    testConfig.setTaxStartDate(yesterday)
    stubFilledInForm
  }

  override protected def afterEach(): Unit = {
    testConfig.resetTaxStartDate()
  }

  private lazy val validStartDateForm = Seq(
    "startDate.day" -> LocalDate.now.getDayOfMonth.toString,
    "startDate.month" -> LocalDate.now.getMonthValue.toString,
    "startDate.year" -> LocalDate.now.getYear.toString
  )
}