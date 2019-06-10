/*
 * Copyright 2019 HM Revenue & Customs
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

import com.softwaremill.macwire._
import org.mockito.ArgumentMatchers.{any, anyString, eq => matching}
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.libs.json._
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import sdil.actions.AuthorisedRequest
import sdil.config.RegistrationFormDataCache
import sdil.models._
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.http.HeaderCarrier
import cats.implicits._
import org.scalatest.Matchers
import play.api.i18n.Messages
import uk.gov.hmrc.http.cache.client.CacheMap

import scala.concurrent._
import scala.concurrent.duration._


class RegistrationControllerSpec extends ControllerSpec with MockitoSugar {

  override lazy val stubMessages: Map[String, Map[String, String]] =
    Map("en" -> Map("heading.partnerships" -> "someOtherValueShouldAppear"))

  lazy val controller: RegistrationController = wire[RegistrationController]
  lazy val controllerTester = new UniformControllerTester(controller)
  implicit val hc: HeaderCarrier = HeaderCarrier()



  def request: AuthorisedRequest[AnyContent] = AuthorisedRequest[AnyContent](
    None, "", Enrolments(Set.empty), FakeRequest()
      .withFormUrlEncodedBody("utr" -> ""))

  "RegistrationController" should {

    "return a 404 when index is called with an empty key" in {
      val index = controller.index("")
      status(index()(request)) mustBe NOT_FOUND
    }

    "redirect to organisation type page when index is called with a key" in {
      stubCacheEntry(Some(defaultFormData))
      val index = controller.index("organisation-type")

      status(index()(request)) mustBe SEE_OTHER
    }

    "execute main program" in {
      def formData = RegistrationFormData(
        RosmRegistration("safeId", None, None,
          Address("", "", "", "", "")
        ),
        "utr"
      )
      submitRegistration()
      val program = controller.program(formData)(request, hc)
      val output = controllerTester.testJourney(program)(
        "contact-details" -> Json.obj(
          "fullName" -> "Fred",
          "position" -> "Smith",
          "phoneNumber" -> "01234 567890",
          "email" -> "fred.smith@ecola.co.uk"
        ),
        "ask-secondary-warehouses" -> JsBoolean(false),
        "production-site-details" -> JsString("Done"),
        "start-date" -> JsString("2018-10-01"),
        "import" -> Json.obj("lower" -> 2345, "higher" -> 56789),
        "pack-at-business-address" -> JsBoolean(true),
        "package-own-uk" -> Json.obj("lower" -> 1000000, "higher" -> 2000000),
        "package-copack" -> Json.obj("lower" -> 110000, "higher" -> 130000),
        "organisation-type" -> JsString("limitedCompany"),
        "producer" -> JsString("Large"),
        "declaration" -> JsNull
      )
      status(output) mustBe OK
    }

    "execute main program and fail js validation when ask-secondary-warehouse is passed as a string" in {
      def formData = RegistrationFormData(
        RosmRegistration("safeId", None, None,
          Address("", "", "", "", "")
        ),
        "utr"
      )

      val program = controller.program(formData)(request, hc)
      val output = controllerTester.testJourney(program)(
        "contact-details" -> Json.obj(
          "fullName" -> "Fred",
          "position" -> "Smith",
          "phoneNumber" -> "01234 567890",
          "email" -> "fred.smith@ecola.co.uk"
        ),
        "ask-secondary-warehouses" -> JsString("hi"),
        "production-site-details" -> JsString("Done"),
        "start-date" -> JsString("2018-10-01"),
        "import" -> Json.obj("lower" -> 2345, "higher" -> 56789),
        "pack-at-business-address" -> JsBoolean(true),
        "package-own-uk" -> Json.obj("lower" -> 1000000, "higher" -> 2000000),
        "package-copack" -> Json.obj("lower" -> 110000, "higher" -> 130000),
        "organisation-type" -> JsString("limitedCompany"),
        "producer" -> JsString("Large")
      )
      intercept[JsResultException] {
        status(output)
      }
    }

    "execute main program and show the partnerships page" in {
      def formData = RegistrationFormData(
        RosmRegistration("safeId", None, None,
          Address("", "", "", "", "")
        ),
        "utr"
      )

      val program = controller.program(formData)(request, hc)
      val output = controllerTester.testJourney(program)(
        "contact-details" -> Json.obj(
          "fullName" -> "Fred",
          "position" -> "Smith",
          "phoneNumber" -> "01234 567890",
          "email" -> "fred.smith@ecola.co.uk"
        ),
        "ask-secondary-warehouses" -> JsBoolean(false),
        "production-site-details" -> JsString("Done"),
        "start-date" -> JsString("2018-10-01"),
        "import" -> Json.obj("lower" -> 2345, "higher" -> 56789),
        "pack-at-business-address" -> JsBoolean(true),
        "package-own-uk" -> Json.obj("lower" -> 1000000, "higher" -> 2000000),
        "package-copack" -> Json.obj("lower" -> 110000, "higher" -> 130000),
        "organisation-type" -> JsString("partnership"),
        "producer" -> JsString("Large")
      )
      status(output) mustBe OK
      contentAsString(output) must include(Messages("heading.partnerships"))
    }

    "execute main program as a small producer who uses a copacker" in {
      def formData = RegistrationFormData(
        RosmRegistration("safeId", None, None,
          Address("", "", "", "", "")
        ),
        "utr"
      )

      val program = controller.program(formData)(request, hc)
      val output = controllerTester.testJourney(program)(
        "contact-details" -> Json.obj(
          "fullName" -> "Fred",
          "position" -> "Smith",
          "phoneNumber" -> "01234 567890",
          "email" -> "fred.smith@ecola.co.uk"
        ),
        "ask-secondary-warehouses" -> JsBoolean(false),
        "production-site-details" -> JsString("Done"),
        "start-date" -> JsString("2018-10-01"),
        "import" -> Json.obj("lower" -> 2345, "higher" -> 56789),
        "pack-at-business-address" -> JsBoolean(true),
        "package-own-uk" -> Json.obj("lower" -> 1000000, "higher" -> 2000000),
        "package-copack" -> Json.obj("lower" -> 110000, "higher" -> 130000),
        "organisation-type" -> JsString("limitedCompany"),
        "copacked" -> JsBoolean(true),
        "producer" -> JsString("Small")
      )
      status(output) mustBe SEE_OTHER
    }

    "execute main program as a small producer who doesn't use a copacker" in {
      def formData = RegistrationFormData(
        RosmRegistration("safeId", None, None,
          Address("", "", "", "", "")
        ),
        "utr"
      )

      val program = controller.program(formData)(request, hc)
      val output = controllerTester.testJourney(program)(
        "contact-details" -> Json.obj(
          "fullName" -> "Fred",
          "position" -> "Smith",
          "phoneNumber" -> "01234 567890",
          "email" -> "fred.smith@ecola.co.uk"
        ),
        "ask-secondary-warehouses" -> JsBoolean(false),
        "production-site-details" -> JsString("Done"),
        "start-date" -> JsString("2018-10-01"),
        "import" -> Json.obj("lower" -> 2345, "higher" -> 56789),
        "pack-at-business-address" -> JsBoolean(true),
        "package-own-uk" -> Json.obj("lower" -> 1000000, "higher" -> 2000000),
        "package-copack" -> Json.obj("lower" -> 110000, "higher" -> 130000),
        "organisation-type" -> JsString("limitedCompany"),
        "copacked" -> JsBoolean(false),
        "producer" -> JsString("Small")
      )
      status(output) mustBe SEE_OTHER
    }

    "execute main program as a user who doesn't packageOwn" in {
      def formData = RegistrationFormData(
        RosmRegistration("safeId", None, None,
          Address("", "", "", "", "")
        ),
        "utr"
      )

      val program = controller.program(formData)(request, hc)
      val output = controllerTester.testJourney(program)(
        "contact-details" -> Json.obj(
          "fullName" -> "Fred",
          "position" -> "Smith",
          "phoneNumber" -> "01234 567890",
          "email" -> "fred.smith@ecola.co.uk"
        ),
        "ask-secondary-warehouses" -> JsBoolean(false),
        "production-site-details" -> JsString("Done"),
        "start-date" -> JsString("2018-10-01"),
        "import" -> Json.obj("lower" -> 2345, "higher" -> 56789),
        "pack-at-business-address" -> JsBoolean(true),
        "package-copack" -> Json.obj("lower" -> 110000, "higher" -> 130000),
        "organisation-type" -> JsString("limitedCompany"),
        "copacked" -> JsBoolean(true),
        "producer" -> JsString("Not")
      )
      status(output) mustBe SEE_OTHER
    }

    "execute main program as a small producer who doesn't use a copacker and has no liable activity" in {
      def formData = RegistrationFormData(
        RosmRegistration("safeId", None, None,
          Address("", "", "", "", "")
        ),
        "utr"
      )

      val program = controller.program(formData)(request, hc)
      val output = controllerTester.testJourney(program)(
        "organisation-type" -> JsString("limitedCompany"),
        "producer" -> JsString("Small"),
        "copacked" -> JsBoolean(false),
        "contact-details" -> Json.obj(
          "fullName" -> "Fred",
          "position" -> "Smith",
          "phoneNumber" -> "01234 567890",
          "email" -> "fred.smith@ecola.co.uk"
        )
      )
      status(output) mustBe SEE_OTHER
    }
  }
}
