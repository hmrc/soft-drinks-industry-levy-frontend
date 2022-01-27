/*
 * Copyright 2022 HM Revenue & Customs
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

/*
 * Copyright 2022 HM Revenue & Customs
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
import org.mockito.ArgumentMatchers.{any, eq => matching, _}
import org.mockito.Mockito._
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import sdil.actions.RegisteredAction
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.allEnrolments

import play.api.mvc.Results.Ok

import sdil.actions.{AuthorisedAction, AuthorisedRequest}

import scala.concurrent.Future

class RegistrationControllerSpec extends ControllerSpec {

  val controller = new RegistrationController(
    authorisedAction,
    mockSdilConnector,
    mockCache,
    stubMessagesControllerComponents,
    testConfig,
    uniformHelpers,
  )

  lazy val testAuthorisedAction: RegisteredAction = wire[RegisteredAction]
  lazy val testAction: Action[AnyContent] = testAuthorisedAction(_ => Ok)
  val fakeRequest: FakeRequest[AnyContent] = FakeRequest()

  val enrolments = Enrolments(Set(new Enrolment("IR-CT", Seq(irCtEnrolment), "Active")))

  def request: AuthorisedRequest[AnyContent] =
    AuthorisedRequest[AnyContent](
      None,
      "",
      Enrolments(Set.empty),
      FakeRequest()
        .withFormUrlEncodedBody("utr" -> ""))

  "RegistrationController" should {

    "return NOT_FOUND when no subscription" in {

      val sdilEnrolment = EnrolmentIdentifier("EtmpRegistrationNumber", "")
      when(mockAuthConnector.authorise[Enrolments](any(), matching(allEnrolments))(any(), any())).thenReturn {
        Future.successful(Enrolments(Set(Enrolment("HMRC-OBTDS-ORG", Seq(sdilEnrolment), "Active"))))
      }

      val result = controller.index("idvalue").apply(FakeRequest())
      status(result) mustEqual NOT_FOUND
    }

    "Redirect to ... when have a full cache" in {

      stubCacheEntry(Some(defaultFormData))

      when(mockSdilConnector.retrieveSubscription(matching(irCtEnrolment.value), anyString())(any())).thenReturn {
        Future.successful(Some(aSubscription))
      }

      when(mockSdilConnector.submit(any(), any())(any())) thenReturn Future.successful(())

      val sdilEnrolment = EnrolmentIdentifier("EtmpRegistrationNumber", "XZSDIL000100107")
      when(mockAuthConnector.authorise[Enrolments](any(), matching(allEnrolments))(any(), any())).thenReturn {
        Future.successful(Enrolments(Set(Enrolment("HMRC-OBTDS-ORG", Seq(sdilEnrolment), "Active"))))
      }

      when(mockSdilConnector.retrieveSubscription(matching("XCSDIL000000002"), anyString())(any())).thenReturn {
        Future.successful(Some(aSubscription))
      }

      val result = controller.index("idvalue").apply(FakeRequest())
      status(result) mustEqual SEE_OTHER
      redirectLocation(result) mustEqual Some("organisation-type")
    }
  }
}
//
//  override lazy val stubMessages: Map[String, Map[String, String]] =
//    Map("en" -> Map("heading.partnerships" -> "someOtherValueShouldAppear"))
//
//  lazy val controller: RegistrationControllerNew = wire[RegistrationControllerNew]
//  lazy val controllerTester = new UniformControllerTester(controller)
//  implicit val hc: HeaderCarrier = HeaderCarrier()
//
//  def request: AuthorisedRequest[AnyContent] =
//    AuthorisedRequest[AnyContent](
//      None,
//      "",
//      Enrolments(Set.empty),
//      FakeRequest()
//        .withFormUrlEncodedBody("utr" -> ""))
//
//  "RegistrationController" should {
//
//    "return a 404 when index is called with an empty key" in {
//      val index = controller.index("")
//      status(index()(request)) mustBe NOT_FOUND
//    }
//
//    "redirect to organisation type page when index is called with a key" in {
//      stubCacheEntry(Some(defaultFormData))
//      val index = controller.index("organisation-type")
//
//      status(index()(request)) mustBe SEE_OTHER
//    }
//
//    "execute main program" in {
//      def formData = RegistrationFormData(
//        RosmRegistration("safeId", None, None, Address("", "", "", "", "")),
//        "utr"
//      )
//      submitRegistration()
//      val program = controller.program(formData)(request, hc)
//      val output = controllerTester.testJourney(program)(
//        "contact-details" -> Json.obj(
//          "fullName"    -> "Fred",
//          "position"    -> "Smith",
//          "phoneNumber" -> "01234 567890",
//          "email"       -> "fred.smith@ecola.co.uk"
//        ),
//        "ask-secondary-warehouses" -> JsBoolean(false),
//        "production-site-details"  -> JsString("Done"),
//        "start-date"               -> JsString("2018-10-01"),
//        "import"                   -> Json.obj("lower" -> 2345, "higher" -> 56789),
//        "pack-at-business-address" -> JsBoolean(true),
//        "package-own-uk"           -> Json.obj("lower" -> 1000000, "higher" -> 2000000),
//        "package-copack"           -> Json.obj("lower" -> 110000, "higher" -> 130000),
//        "organisation-type"        -> JsString("limitedCompany"),
//        "producer"                 -> JsString("Large"),
//        "declaration"              -> JsNull
//      )
//      status(output) mustBe OK
//    }
//
//    "execute main program and fail js validation when ask-secondary-warehouse is passed as a string" in {
//      def formData = RegistrationFormData(
//        RosmRegistration("safeId", None, None, Address("", "", "", "", "")),
//        "utr"
//      )
//
//      val program = controller.program(formData)(request, hc)
//      val output = controllerTester.testJourney(program)(
//        "contact-details" -> Json.obj(
//          "fullName"    -> "Fred",
//          "position"    -> "Smith",
//          "phoneNumber" -> "01234 567890",
//          "email"       -> "fred.smith@ecola.co.uk"
//        ),
//        "ask-secondary-warehouses" -> JsString("hi"),
//        "production-site-details"  -> JsString("Done"),
//        "start-date"               -> JsString("2018-10-01"),
//        "import"                   -> Json.obj("lower" -> 2345, "higher" -> 56789),
//        "pack-at-business-address" -> JsBoolean(true),
//        "package-own-uk"           -> Json.obj("lower" -> 1000000, "higher" -> 2000000),
//        "package-copack"           -> Json.obj("lower" -> 110000, "higher" -> 130000),
//        "organisation-type"        -> JsString("limitedCompany"),
//        "producer"                 -> JsString("Large")
//      )
//      intercept[JsResultException] {
//        status(output)
//      }
//    }
//
//    "execute main program and show the partnerships page" in {
//      def formData = RegistrationFormData(
//        RosmRegistration("safeId", None, None, Address("", "", "", "", "")),
//        "utr"
//      )
//
//      val program = controller.program(formData)(request, hc)
//      val output = controllerTester.testJourney(program)(
//        "contact-details" -> Json.obj(
//          "fullName"    -> "Fred",
//          "position"    -> "Smith",
//          "phoneNumber" -> "01234 567890",
//          "email"       -> "fred.smith@ecola.co.uk"
//        ),
//        "ask-secondary-warehouses" -> JsBoolean(false),
//        "production-site-details"  -> JsString("Done"),
//        "start-date"               -> JsString("2018-10-01"),
//        "import"                   -> Json.obj("lower" -> 2345, "higher" -> 56789),
//        "pack-at-business-address" -> JsBoolean(true),
//        "package-own-uk"           -> Json.obj("lower" -> 1000000, "higher" -> 2000000),
//        "package-copack"           -> Json.obj("lower" -> 110000, "higher" -> 130000),
//        "organisation-type"        -> JsString("partnership"),
//        "producer"                 -> JsString("Large")
//      )
//      status(output) mustBe OK
//      contentAsString(output) must include(Messages("heading.partnerships"))
//    }
//
//    "execute main program as a small producer who uses a copacker" in {
//      def formData = RegistrationFormData(
//        RosmRegistration("safeId", None, None, Address("", "", "", "", "")),
//        "utr"
//      )
//
//      val program = controller.program(formData)(request, hc)
//      val output = controllerTester.testJourney(program)(
//        "contact-details" -> Json.obj(
//          "fullName"    -> "Fred",
//          "position"    -> "Smith",
//          "phoneNumber" -> "01234 567890",
//          "email"       -> "fred.smith@ecola.co.uk"
//        ),
//        "ask-secondary-warehouses" -> JsBoolean(false),
//        "production-site-details"  -> JsString("Done"),
//        "start-date"               -> JsString("2018-10-01"),
//        "import"                   -> Json.obj("lower" -> 2345, "higher" -> 56789),
//        "pack-at-business-address" -> JsBoolean(true),
//        "package-own-uk"           -> Json.obj("lower" -> 1000000, "higher" -> 2000000),
//        "package-copack"           -> Json.obj("lower" -> 110000, "higher" -> 130000),
//        "organisation-type"        -> JsString("limitedCompany"),
//        "copacked"                 -> JsBoolean(true),
//        "producer"                 -> JsString("Small")
//      )
//      status(output) mustBe SEE_OTHER
//    }
//
//    "execute main program as a small producer who doesn't use a copacker" in {
//      def formData = RegistrationFormData(
//        RosmRegistration("safeId", None, None, Address("", "", "", "", "")),
//        "utr"
//      )
//
//      val program = controller.program(formData)(request, hc)
//      val output = controllerTester.testJourney(program)(
//        "contact-details" -> Json.obj(
//          "fullName"    -> "Fred",
//          "position"    -> "Smith",
//          "phoneNumber" -> "01234 567890",
//          "email"       -> "fred.smith@ecola.co.uk"
//        ),
//        "ask-secondary-warehouses" -> JsBoolean(false),
//        "production-site-details"  -> JsString("Done"),
//        "start-date"               -> JsString("2018-10-01"),
//        "import"                   -> Json.obj("lower" -> 2345, "higher" -> 56789),
//        "pack-at-business-address" -> JsBoolean(true),
//        "package-own-uk"           -> Json.obj("lower" -> 1000000, "higher" -> 2000000),
//        "package-copack"           -> Json.obj("lower" -> 110000, "higher" -> 130000),
//        "organisation-type"        -> JsString("limitedCompany"),
//        "copacked"                 -> JsBoolean(false),
//        "producer"                 -> JsString("Small")
//      )
//      status(output) mustBe SEE_OTHER
//    }
//
//    "execute main program as a user who doesn't packageOwn" in {
//      def formData = RegistrationFormData(
//        RosmRegistration("safeId", None, None, Address("", "", "", "", "")),
//        "utr"
//      )
//
//      val program = controller.program(formData)(request, hc)
//      val output = controllerTester.testJourney(program)(
//        "contact-details" -> Json.obj(
//          "fullName"    -> "Fred",
//          "position"    -> "Smith",
//          "phoneNumber" -> "01234 567890",
//          "email"       -> "fred.smith@ecola.co.uk"
//        ),
//        "ask-secondary-warehouses" -> JsBoolean(false),
//        "production-site-details"  -> JsString("Done"),
//        "start-date"               -> JsString("2018-10-01"),
//        "import"                   -> Json.obj("lower" -> 2345, "higher" -> 56789),
//        "pack-at-business-address" -> JsBoolean(true),
//        "package-copack"           -> Json.obj("lower" -> 110000, "higher" -> 130000),
//        "organisation-type"        -> JsString("limitedCompany"),
//        "copacked"                 -> JsBoolean(true),
//        "producer"                 -> JsString("Not")
//      )
//      status(output) mustBe SEE_OTHER
//    }
//
//    "execute main program as a small producer who doesn't use a copacker and has no liable activity" in {
//      def formData = RegistrationFormData(
//        RosmRegistration("safeId", None, None, Address("", "", "", "", "")),
//        "utr"
//      )
//
//      val program = controller.program(formData)(request, hc)
//      val output = controllerTester.testJourney(program)(
//        "organisation-type" -> JsString("limitedCompany"),
//        "producer"          -> JsString("Small"),
//        "copacked"          -> JsBoolean(false),
//        "contact-details" -> Json.obj(
//          "fullName"    -> "Fred",
//          "position"    -> "Smith",
//          "phoneNumber" -> "01234 567890",
//          "email"       -> "fred.smith@ecola.co.uk"
//        )
//      )
//      status(output) mustBe SEE_OTHER
//    }
//  }
//}
