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

import java.time.LocalDate

import org.jsoup.Jsoup
import org.mockito.ArgumentMatchers.{any, eq => matching}
import org.mockito.Mockito.when
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import sdil.models.Packaging
import sdil.utils.TestConfig

import scala.concurrent.Future

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
      stubCacheEntry[Packaging]("packaging", Some(packagingIsLiable))

      val request = FakeRequest().withFormUrlEncodedBody(validStartDateForm: _*)

      val response = controller.submitStartDate().apply(request)

      status(response) mustBe SEE_OTHER
      redirectLocation(response).get mustBe routes.ProductionSiteController.addSite().url
    }

    "return Status: See Other for start date form POST with valid date and redirect to secondary warehouse page" in {
      when(mockCache.fetchAndGetEntry[Packaging](matching("packaging"))(any(), any(), any()))
        .thenReturn(Future.successful(Some(packagingIsntLiable)))
      val request = FakeRequest().withFormUrlEncodedBody(validStartDateForm: _*)
      val response = controller.submitStartDate().apply(request)

      status(response) mustBe SEE_OTHER
      redirectLocation(response).get mustBe routes.WarehouseController.secondaryWarehouse().url
    }

    "return Status: Bad Request for invalid start date form POST request with day too low and display field hint" in {
      val request = FakeRequest().withFormUrlEncodedBody(
        "startDateDay" -> "-2",
        "startDateMonth" -> "08",
        "startDateYear" -> "2017"
      )
      val response = controller.submitStartDate().apply(request)

      status(response) mustBe BAD_REQUEST
      contentType(response).get mustBe HTML
      contentAsString(response) must include(messagesApi("error.start-date.day-too-low"))
    }

    "return Status: Bad Request for invalid start date form POST request with day too high and display field hint" in {
      val request = FakeRequest().withFormUrlEncodedBody(
        "startDateDay" -> "35",
        "startDateMonth" -> "08",
        "startDateYear" -> "2017")
      val response = controller.submitStartDate().apply(request)

      status(response) mustBe BAD_REQUEST
      contentType(response).get mustBe HTML
      contentAsString(response) must include(messagesApi("error.start-date.day-too-high"))
    }

    "return Status: Bad Request for invalid start date form POST request with month too low and display field hint" in {
      val request = FakeRequest().withFormUrlEncodedBody(
        "startDateDay" -> "20",
        "startDateMonth" -> "-59",
        "startDateYear" -> "2017"
      )
      val response = controller.submitStartDate().apply(request)

      status(response) mustBe BAD_REQUEST
      contentType(response).get mustBe HTML
      contentAsString(response) must include(messagesApi("error.start-date.month-too-low"))
    }

    "return Status: Bad Request for invalid start date form POST request with month too high and display field hint" in {
      val request = FakeRequest().withFormUrlEncodedBody(
        "startDateDay" -> "20",
        "startDateMonth" -> "30",
        "startDateYear" -> "2017"
      )
      val response = controller.submitStartDate().apply(request)

      status(response) mustBe BAD_REQUEST
      contentType(response).get mustBe HTML
      contentAsString(response) must include(messagesApi("error.start-date.month-too-high"))
    }

    "return Status: Bad Request for invalid start date form POST request with year too low and display field hint" in {
      val request = FakeRequest().withFormUrlEncodedBody(
        "startDateDay" -> "20",
        "startDateMonth" -> "02",
        "startDateYear" -> "2007"
      )
      val response = controller.submitStartDate().apply(request)

      status(response) mustBe BAD_REQUEST
      contentType(response).get mustBe HTML
      contentAsString(response) must include(messagesApi("error.start-date.year-too-low"))
    }

    "return Status: Bad Request for invalid start date form POST request with year too high and display field hint" in {
      val request = FakeRequest().withFormUrlEncodedBody(
        "startDateDay" -> "20",
        "startDateMonth" -> "02",
        "startDateYear" -> "20189"
      )
      val response = controller.submitStartDate().apply(request)

      status(response) mustBe BAD_REQUEST
      contentType(response).get mustBe HTML
      contentAsString(response) must include(messagesApi("error.start-date.year-too-high"))
    }

    "return Status: Bad Request for invalid start date form POST request with invalid date and display field hint" in {
      val request = FakeRequest().withFormUrlEncodedBody(
        "startDateDay" -> "29",
        "startDateMonth" -> "02",
        "startDateYear" -> "2017"
      )
      val response = controller.submitStartDate().apply(request)

      status(response) mustBe BAD_REQUEST
      contentType(response).get mustBe HTML
      contentAsString(response) must include(messagesApi("error.start-date.date-invalid"))
    }

    "return Status: Bad Request for invalid start date form POST request with date in future and display field hint" in {
      val request = FakeRequest().withFormUrlEncodedBody(
        "startDateDay" -> "20",
        "startDateMonth" -> "12",
        "startDateYear" -> "9999")
      val response = controller.submitStartDate().apply(request)

      status(response) mustBe BAD_REQUEST
      contentType(response).get mustBe HTML
      contentAsString(response) must include(messagesApi("error.start-date.date-too-high"))
    }

    "return Status: Bad Request for invalid start date form POST request with date in past and display field hint" in {
      val request = FakeRequest().withFormUrlEncodedBody(
        "startDateDay" -> "22",
        "startDateMonth" -> "06",
        "startDateYear" -> "2017"
      )
      val response = controller.submitStartDate().apply(request)

      status(response) mustBe BAD_REQUEST
      contentType(response).get mustBe HTML
      contentAsString(response) must include(messagesApi("error.start-date.date-too-low"))
    }

    "return a page with a link back to the import volume page if the user imports liable drinks" in {
      when(mockCache.fetchAndGetEntry[Boolean](matching("import"))(any(), any(), any()))
        .thenReturn(Future.successful(Some(true)))

      val response = controller.displayStartDate(FakeRequest())
      status(response) mustBe OK

      val html = Jsoup.parse(contentAsString(response))
      html.select("a.link-back").attr("href") mustBe routes.LitreageController.show("importVolume").url
    }

    "return a page with a link back to the imports page if the user does not import liable drinks" in {
      when(mockCache.fetchAndGetEntry[Boolean](matching("import"))(any(), any(), any()))
        .thenReturn(Future.successful(Some(false)))

      val response = controller.displayStartDate(FakeRequest())
      status(response) mustBe OK

      val html = Jsoup.parse(contentAsString(response))
      html.select("a.link-back").attr("href") mustBe routes.RadioFormController.display("import", "importVolume", "production-sites").url
    }

    "return Status: See Other for start date form GET with valid date and Liable booleans with redirect to add site page" in {
      stubCacheEntry[Packaging]("packaging", Some(packagingIsLiable))
      futureTaxStartDate()

      val request = FakeRequest().withFormUrlEncodedBody(validStartDateForm: _*)

      val response = controller.displayStartDate().apply(request)
      status(response) mustBe SEE_OTHER
      redirectLocation(response).get mustBe routes.ProductionSiteController.addSite().url
    }

    "return Status: See Other for start date form GET with valid date and no Liable booleans with redirect to display Package page" in {
      stubCacheEntry[Packaging]("packaging", None)
      futureTaxStartDate()

      val request = FakeRequest().withFormUrlEncodedBody(validStartDateForm: _*)

      val response = controller.displayStartDate().apply(request)
      status(response) mustBe SEE_OTHER
      redirectLocation(response).get mustBe routes.PackageController.displayPackage().url
    }

    "return Status: See Other for start date form GET with valid date and isnt Liable booleans with redirect to secondary warehouse page" in {
      stubCacheEntry[Packaging]("packaging", Some(packagingIsntLiable))
      futureTaxStartDate()

      val request = FakeRequest().withFormUrlEncodedBody(validStartDateForm: _*)

      val response = controller.displayStartDate().apply(request)
      status(response) mustBe SEE_OTHER
      redirectLocation(response).get mustBe routes.WarehouseController.secondaryWarehouse().url
    }
  }

  private lazy val packagingIsLiable = Packaging(true, true, false)
  private lazy val packagingIsntLiable = Packaging(false, false, false)

  private def futureTaxStartDate(): Unit = {
    TestConfig.setTaxStartDate(LocalDate.now plusDays 1)
  }

  override protected def beforeEach(): Unit = {
    TestConfig.setTaxStartDate(LocalDate.now minusDays 1)
  }

  override protected def afterEach(): Unit = {
    TestConfig.resetTaxStartDate()
  }

  private lazy val validStartDateForm = Seq(
    "startDateDay" -> LocalDate.now.getDayOfMonth.toString,
    "startDateMonth" -> LocalDate.now.getMonthValue.toString,
    "startDateYear" -> LocalDate.now.getYear.toString
  )
}