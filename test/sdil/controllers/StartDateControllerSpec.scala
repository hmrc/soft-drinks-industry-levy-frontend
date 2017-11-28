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

import org.mockito.ArgumentMatchers.{any, eq => matching}
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.test.FakeRequest
import play.api.test.Helpers._
import sdil.controllers.controllerhelpers._
import sdil.models.Packaging
import uk.gov.hmrc.http.cache.client.SessionCache

import scala.concurrent.Future

class StartDateControllerSpec extends PlayMessagesSpec with MockitoSugar with BeforeAndAfterEach {

  val controller = new StartDateController(messagesApi, controllerhelpers.dateAfterTaxStart) {
    override val cache: SessionCache = mockCache
  }

  "StartDateController" should {
    "return Status: 200 when user is logged in and loads start date page" in {
      val request = FakeRequest("GET", "/start-date")
      val result = controller.displayStartDate.apply(request)

      status(result) mustBe OK
      contentAsString(result) must include(messagesApi("sdil.start-date.heading"))
    }

    "return Status: See Other for start date form POST with valid date and redirect to add site page" in {
      when(mockCache.fetchAndGetEntry[Packaging](matching("packaging"))(any(), any(), any()))
        .thenReturn(Future.successful(Some(packagingIsLiable)))
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

    "return Status: Bad Request for invalid start date form POST request with invalid day and display field hint .." in {
      val request = FakeRequest().withFormUrlEncodedBody(invalidStartDateDayForm: _*)
      val response = controller.submitStartDate().apply(request)

      status(response) mustBe BAD_REQUEST
      contentType(response).get mustBe HTML
      contentAsString(response) must include(messagesApi("error.start-date.day-too-high"))
    }

    "return Status: Bad Request for invalid start date form POST request with invalid month and display field hint .." in {
      val request = FakeRequest().withFormUrlEncodedBody(invalidStartDateMonthForm: _*)
      val response = controller.submitStartDate().apply(request)

      status(response) mustBe BAD_REQUEST
      contentType(response).get mustBe HTML
      contentAsString(response) must include(messagesApi("error.start-date.month-too-high"))
    }

    "return Status: Bad Request for invalid start date form POST request with invalid year and display field hint .." in {
      val request = FakeRequest().withFormUrlEncodedBody(invalidStartDateYearForm: _*)
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
  }
}