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

import org.mockito.ArgumentMatchers.{any, anyString}
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.test.FakeRequest
import play.api.test.Helpers._
import sdil.connectors.SoftDrinksIndustryLevyConnector
import sdil.controllers.controllerhelpers._
import uk.gov.hmrc.http.cache.client.{CacheMap, SessionCache}

import scala.concurrent.Future

class SDILControllerSpec extends PlayMessagesSpec with MockitoSugar with GuiceOneAppPerSuite with BeforeAndAfterEach {

  val mockSdilConnector: SoftDrinksIndustryLevyConnector = mock[SoftDrinksIndustryLevyConnector]
  val controller = new SDILController(messagesApi, mockSdilConnector) {
    override def authConnector = mockAuthConnector

    override val cache: SessionCache = mockCache
  }

  override def beforeEach() {
    reset(mockSdilConnector, mockAuthConnector)
  }

  "SDILController" should {
    "return Status: 200 Message: Return result is: true for successful auth with utr" in {
      sdilAuthMock(userWithUtr)
      val request = FakeRequest("GET", "/auth-test")
      val result = controller.testAuth.apply(request)

      status(result) mustBe OK
      contentAsString(result) must include("Return result is: true")
    }

    "return Status: SEE_OTHER when user logged in without utr and redirect to Sign In page" in {
      sdilAuthMock(userNoUtr)
      val request = FakeRequest("GET", "/auth-test")
      val result = controller.testAuth.apply(request)

      status(result) mustBe SEE_OTHER
      redirectLocation(result).get must include("gg/sign-in")
    }

    "return Status: SEE_OTHER when user not logged in and redirect to Sign In page" in {
      sdilAuthMock(notLoggedIn)
      val request = FakeRequest("GET", "/auth-test")
      val result = controller.testAuth.apply(request)

      status(result) mustBe SEE_OTHER
      redirectLocation(result).get must include("gg/sign-in")
    }

    "return Status: 200 when user is logged in and loads package page" in {
      sdilAuthMock(userWithUtr)
      val request = FakeRequest("GET", "/package")
      val result = controller.showPackage().apply(request)

      status(result) mustBe OK
      contentAsString(result) must include(messagesApi("sdil.package.heading"))
    }

    "return Status: See Other for package form POST with isLiable & ownBrands and redirect to package own" in {
      val request = FakeRequest().withFormUrlEncodedBody(validPackageForm: _*)
      val response = controller.submitPackage().apply(request)

      status(response) mustBe SEE_OTHER
      redirectLocation(response).get mustBe routes.SDILController.showPackageOwn().url
    }

    "return Status: See Other for package form POST with isLiable & customers and redirect to package copack" in {
      val request = FakeRequest().withFormUrlEncodedBody(validPackageFormCustomersOnly: _*)
      val response = controller.submitPackage().apply(request)

      status(response) mustBe SEE_OTHER
      redirectLocation(response).get mustBe routes.SDILController.showPackageCopack().url
    }

    "return Status: Bad Request for invalid liability form POST request and display field hint .." in {
      val request = FakeRequest().withFormUrlEncodedBody(invalidPackageForm: _*)
      val response = controller.submitPackage().apply(request)

      status(response) mustBe BAD_REQUEST
      contentType(response).get mustBe HTML
      contentAsString(response) must include(messagesApi("sdil.form.radiocheck.error.summary"))
    }

    "return Status: OK for contact details form GET" in {
      val request = FakeRequest("GET", "/contact-details")
      val result = controller.displayContactDetails.apply(request)

      status(result) mustBe OK
      contentAsString(result) must include(messagesApi("sdil.contact-details.heading"))
    }

    "return Status: SEE_OTHER for valid contact details form POST" in {
      val request = FakeRequest("POST", "/contact-details").withFormUrlEncodedBody(validContactDetailsForm: _*)
      val result = controller.submitContactDetails.apply(request)

      status(result) mustBe SEE_OTHER
      redirectLocation(result).get mustBe routes.SDILController.displayDeclaration().url
    }

    "return Status: BAD_REQUEST for invalid contact details form POST" in {
      val request = FakeRequest("POST", "/contact-details").withFormUrlEncodedBody(invalidContactDetailsForm: _*)
      val result = controller.submitContactDetails.apply(request)

      status(result) mustBe BAD_REQUEST
      contentAsString(result) must include(messagesApi("error.full-name.invalid"))
    }
  }

  lazy val mockCache = {
    val m = mock[SessionCache]
    when(m.cache(anyString(), any())(any(), any(), any())).thenReturn(Future.successful(CacheMap("", Map.empty)))
    m
  }
}
