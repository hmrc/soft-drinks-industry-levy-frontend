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

import org.mockito.ArgumentMatchers.{any, eq => matching}
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterAll
import play.api.test.FakeRequest
import play.api.test.Helpers._
import sdil.controllers.controllerhelpers._
import sdil.models.Packaging
import sdil.utils.TestConfig

import scala.concurrent.Future

class StartDateControllerSpec extends ControllerSpec with BeforeAndAfterAll {

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

    "return Status: Ok and redirect to addSite page for liable form in past" in {
      stubCacheEntry[Packaging]("packaging", Some(packagingIsLiable))
      TestConfig.setTaxStartDate (LocalDate.now minusDays 1)

      val request = FakeRequest().withFormUrlEncodedBody(invalidStartDatePastForm: _*)

      val response = controller.displayStartDate().apply(request)

      contentAsString(response) must include(messagesApi("sdil.address.line1"))
    }

    "return Status: See Other for start date form POST with valid date and redirect to secondary warehouse page" in {
      when(mockCache.fetchAndGetEntry[Packaging](matching("packaging"))(any(), any(), any()))
        .thenReturn(Future.successful(Some(packagingIsntLiable)))
      val request = FakeRequest().withFormUrlEncodedBody(validStartDateForm: _*)
      val response = controller.submitStartDate().apply(request)

      status(response) mustBe SEE_OTHER
      redirectLocation(response).get mustBe routes.WarehouseController.secondaryWarehouse().url
    }

    "return Status: Bad Request for invalid start date form POST request with day too low and display field hint .." in {
      val request = FakeRequest().withFormUrlEncodedBody(invalidStartDateDayTooLowForm: _*)
      val response = controller.submitStartDate().apply(request)

      status(response) mustBe BAD_REQUEST
      contentType(response).get mustBe HTML
      contentAsString(response) must include(messagesApi("error.start-date.day-too-low"))
    }

    "return Status: Bad Request for invalid start date form POST request with day too high and display field hint .." in {
      val request = FakeRequest().withFormUrlEncodedBody(invalidStartDateDayTooHighForm: _*)
      val response = controller.submitStartDate().apply(request)

      status(response) mustBe BAD_REQUEST
      contentType(response).get mustBe HTML
      contentAsString(response) must include(messagesApi("error.start-date.day-too-high"))
    }

    "return Status: Bad Request for invalid start date form POST request with month too low and display field hint .." in {
      val request = FakeRequest().withFormUrlEncodedBody(invalidStartDateMonthTooLowForm: _*)
      val response = controller.submitStartDate().apply(request)

      status(response) mustBe BAD_REQUEST
      contentType(response).get mustBe HTML
      contentAsString(response) must include(messagesApi("error.start-date.month-too-low"))
    }

    "return Status: Bad Request for invalid start date form POST request with month too high and display field hint .." in {
      val request = FakeRequest().withFormUrlEncodedBody(invalidStartDateMonthTooHighForm: _*)
      val response = controller.submitStartDate().apply(request)

      status(response) mustBe BAD_REQUEST
      contentType(response).get mustBe HTML
      contentAsString(response) must include(messagesApi("error.start-date.month-too-high"))
    }

    "return Status: Bad Request for invalid start date form POST request with year too low and display field hint .." in {
      val request = FakeRequest().withFormUrlEncodedBody(invalidStartDateYearTooLowForm: _*)
      val response = controller.submitStartDate().apply(request)

      status(response) mustBe BAD_REQUEST
      contentType(response).get mustBe HTML
      contentAsString(response) must include(messagesApi("error.start-date.year-too-low"))
    }

    "return Status: Bad Request for invalid start date form POST request with year too high and display field hint .." in {
      val request = FakeRequest().withFormUrlEncodedBody(invalidStartDateYearTooHighForm: _*)
      val response = controller.submitStartDate().apply(request)

      status(response) mustBe BAD_REQUEST
      contentType(response).get mustBe HTML
      contentAsString(response) must include(messagesApi("error.start-date.year-too-high"))
    }

    "return Status: Bad Request for invalid start date form POST request with invalid date and display field hint .." in {
      val request = FakeRequest().withFormUrlEncodedBody(invalidStartDateForm: _*)
      val response = controller.submitStartDate().apply(request)

      status(response) mustBe BAD_REQUEST
      contentType(response).get mustBe HTML
      contentAsString(response) must include(messagesApi("error.start-date.date-invalid"))
    }

  "return Status: Bad Request for invalid start date form POST request with date in future and display field hint .." in {
    val request = FakeRequest().withFormUrlEncodedBody(invalidStartDateFutureForm: _*)
    val response = controller.submitStartDate().apply(request)

    status(response) mustBe BAD_REQUEST
    contentType(response).get mustBe HTML
    contentAsString(response) must include(messagesApi("error.start-date.date-too-high"))
  }
    "return Status: Bad Request for invalid start date form POST request with date in past and display field hint .." in {
    val request = FakeRequest().withFormUrlEncodedBody(invalidStartDatePastForm: _*)
    val response = controller.submitStartDate().apply(request)

    status(response) mustBe BAD_REQUEST
    contentType(response).get mustBe HTML
    contentAsString(response) must include(messagesApi("error.start-date.date-too-low"))
  }
}

override protected def beforeAll (): Unit = {
  TestConfig.setTaxStartDate (LocalDate.now minusDays 1)
}

  override protected def afterAll (): Unit = {
  TestConfig.resetTaxStartDate ()
}
}