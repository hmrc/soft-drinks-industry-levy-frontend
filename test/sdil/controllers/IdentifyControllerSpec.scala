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
import org.mockito.ArgumentMatchers.{any, eq => matching}
import org.mockito.Mockito._
import play.api.i18n.Messages
import play.api.test.FakeRequest
import play.api.test.Helpers._
import sdil.models.RegistrationFormData
import uk.gov.hmrc.auth.core.AffinityGroup.Organisation
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.~

import scala.concurrent.Future

class IdentifyControllerSpec extends ControllerSpec {

  "GET /utr" should {

    "redirect to the verify page if the user has a previous session" in {
      stubFilledInForm

      stubAuthResult(new ~(Enrolments(Set.empty), Some(User)))

      val res = testController.start()(FakeRequest())
      status(res) mustBe SEE_OTHER

      redirectLocation(res).value mustBe routes.VerifyController.show().url

      stubCacheEntry(None)
    }

    "look up the business partner record in ROSM if the user has an existing UTR enrolment" in {
      val irctEnrolment = Enrolments(Set(Enrolment("IR-CT", Seq(EnrolmentIdentifier("UTR", "1122334456")), "Active")))

      stubAuthResult(new ~(irctEnrolment, Some(User)))

      val res = testController.start()(FakeRequest())
      status(res) mustBe SEE_OTHER

      verify(mockSdilConnector, times(1)).getRosmRegistration(matching("1122334456"))(any())
    }

    "redirect to the verify page if the user has a IR-CT enrolment" in {
      val irctEnrolment = Enrolments(Set(Enrolment("IR-CT", Seq(EnrolmentIdentifier("UTR", "1234567891")), "Active")))

      stubAuthResult(new ~(irctEnrolment, Some(User)))

      val res = testController.start()(FakeRequest())
      status(res) mustBe SEE_OTHER
      redirectLocation(res).value mustBe routes.VerifyController.show().url
    }

    "store the UTR and BPR in keystore if the user has an IR-CT enrolment" in {
      val ctEnrolment = Enrolments(Set(Enrolment("IR-CT", Seq(EnrolmentIdentifier("UTR", "1234567892")), "Active")))

      stubAuthResult(new ~(ctEnrolment, Some(User)))

      val res = testController.start()(FakeRequest())
      status(res) mustBe SEE_OTHER

      verifyDataCached(RegistrationFormData(defaultRosmData, "1234567892"))
    }

    "redirect to the verify page if the user has an IR-SA enrolment" in {
      val saEnrolment = Enrolments(Set(Enrolment("IR-SA", Seq(EnrolmentIdentifier("UTR", "1234567893")), "Active")))

      stubAuthResult(new ~(saEnrolment, Some(User)))

      val res = testController.start()(FakeRequest())
      status(res) mustBe SEE_OTHER
      redirectLocation(res).value mustBe routes.VerifyController.show().url
    }

    "store the UTR and BPR in keystore if the user has an IR-SA enrolment" in {
      val saEnrolment = Enrolments(Set(Enrolment("IR-SA", Seq(EnrolmentIdentifier("UTR", "1234567894")), "Active")))

      stubAuthResult(new ~(saEnrolment, Some(User)))

      val res = testController.start()(FakeRequest())
      status(res) mustBe SEE_OTHER

      verifyDataCached(RegistrationFormData(defaultRosmData, "1234567894"))
    }

    "redirect to the identify page if the user does not have a UTR enrolment" in {
      stubAuthResult(new ~(Enrolments(Set.empty), Some(User)))

      val res = testController.start()(FakeRequest())
      status(res) mustBe SEE_OTHER

      redirectLocation(res).value mustBe routes.IdentifyController.show().url
    }

    "redirect to the identify page if the user has a UTR enrolment, but no record in ROSM" in {
      val irctEnrolment = Enrolments(Set(Enrolment("IR-CT", Seq(EnrolmentIdentifier("UTR", "3344556677")), "Active")))

      stubAuthResult(new ~(irctEnrolment, Some(User)))
      when(mockSdilConnector.getRosmRegistration(matching("3344556677"))(any())).thenReturn(Future.successful(None))

      val res = testController.start()(FakeRequest())
      status(res) mustBe SEE_OTHER

      redirectLocation(res).value mustBe routes.IdentifyController.show().url
    }
  }

  "GET /identify" should {
    "always return 200 Ok and the identify page" in {
      val res = testController.show()(FakeRequest())
      status(res) mustBe OK
      contentAsString(res) must include(Messages("sdil.identify.heading"))
    }
  }

  "POST /identify" should {
    "return 400 - Bad Request and the identify page when the form data is invalid" in {
      val request = FakeRequest().withFormUrlEncodedBody("utr" -> "", "postcode" -> "")
      val res = testController.submit()(request)

      status(res) mustBe BAD_REQUEST
      contentAsString(res) must include(Messages("sdil.identify.heading"))
    }

    "return 400 - Bad Request and the identify page if there is no record in ROSM for the entered UTR" in {
      when(mockSdilConnector.getRosmRegistration(matching("2233445566"))(any())).thenReturn(Future.successful(None))

      val request = FakeRequest().withFormUrlEncodedBody("utr" -> "2233445566", "postcode" -> "AA11 1AA")
      val res = testController.submit()(request)

      status(res) mustBe BAD_REQUEST
      contentAsString(res) must include(Messages("error.utr.no-record"))
    }

    "return 400 - Bad Request and the identify page if the postcode does not match ROSM's business partner record" in {
      val rosmData = defaultRosmData.copy(address = defaultRosmData.address.copy(postcode = "AA12 2AA"))

      when(mockSdilConnector.getRosmRegistration(matching("4455667788"))(any()))
        .thenReturn(Future.successful(Some(rosmData)))

      val request = FakeRequest().withFormUrlEncodedBody("utr" -> "4455667788", "postcode" -> "AA11 1AA")
      val res = testController.submit()(request)

      status(res) mustBe BAD_REQUEST
      contentAsString(res) must include(Messages("error.utr.no-record"))
    }

    "redirect to the verify page if the form data is valid" in {
      val request = FakeRequest().withFormUrlEncodedBody("utr" -> "1122334455", "postcode" -> "AA11 1AA")
      val res = testController.submit()(request)

      status(res) mustBe SEE_OTHER
      redirectLocation(res).value mustBe routes.VerifyController.show().url
    }

    "match against the BPR postcode if the entered postcode does not contain a space" in {
      val request = FakeRequest().withFormUrlEncodedBody("utr" -> "1122334455", "postcode" -> "AA111AA")
      val res = testController.submit()(request)

      status(res) mustBe SEE_OTHER
      redirectLocation(res).value mustBe routes.VerifyController.show().url
    }

    "match against the BPR postcode if the entered postcode is lower case" in {
      val request = FakeRequest().withFormUrlEncodedBody("utr" -> "1122334455", "postcode" -> "aa11 1aa")
      val res = testController.submit()(request)

      status(res) mustBe SEE_OTHER
      redirectLocation(res).value mustBe routes.VerifyController.show().url
    }

    "store the UTR and business partner record in keystore if the form data is valid" in {
      val request = FakeRequest().withFormUrlEncodedBody("utr" -> "1234567890", "postcode" -> "AA11 1AA")
      val res = testController.submit()(request)

      status(res) mustBe SEE_OTHER

      verify(mockCache, times(1)).cache(
        matching("internal id"),
        matching(RegistrationFormData(defaultRosmData, "1234567890"))
      )(any())
    }
  }

  lazy val testController = wire[IdentifyController]

  def stubAuthResult(res: Enrolments ~ Option[CredentialRole]) =
    when(mockAuthConnector.authorise[Retrieval](any(), any())(any(), any())).thenReturn {
      Future.successful(new ~(new ~(res, Some("internal id")), Some(Organisation)))
    }
}
