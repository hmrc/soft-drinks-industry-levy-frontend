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

import
org.mockito.ArgumentMatchers.{any, eq => matching}
import org.mockito.Mockito._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import sdil.models.{Identification, RegistrationFormData}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.~

import scala.concurrent.Future

class IdentifyControllerSpec extends ControllerSpec {

  "GET /utr" should {
    "redirect to the verify page if the user has a IR-CT enrolment" in {
      val irctEnrolment = Enrolments(Set(Enrolment("IR-CT", Seq(EnrolmentIdentifier("UTR", "1234567891")), "Active")))

      stubAuthResult(Future.successful(new ~(irctEnrolment, Some(User))))

      val res = testController.getUtr()(FakeRequest())
      status(res) mustBe SEE_OTHER
      redirectLocation(res).value mustBe routes.VerifyController.verify().url
    }

    "store the UTR in keystore if the user has an IR-CT enrolment" in {
      val ctEnrolment = Enrolments(Set(Enrolment("IR-CT", Seq(EnrolmentIdentifier("UTR", "1234567892")), "Active")))

      stubAuthResult(Future.successful(new ~(ctEnrolment, Some(User))))

      val res = testController.getUtr()(FakeRequest())
      status(res) mustBe SEE_OTHER

      verifyDataCached(RegistrationFormData(Identification("1234567892", "AA11 1AA")))
    }

    "redirect to the verify page if the user has an IR-SA enrolment" in {
      val saEnrolment = Enrolments(Set(Enrolment("IR-SA", Seq(EnrolmentIdentifier("UTR", "1234567893")), "Active")))

      stubAuthResult(Future.successful(new ~(saEnrolment, Some(User))))

      val res = testController.getUtr()(FakeRequest())
      status(res) mustBe SEE_OTHER
      redirectLocation(res).value mustBe routes.VerifyController.verify().url
    }

    "store the UTR in keystore if the user has an IR-SA enrolment" in {
      val saEnrolment = Enrolments(Set(Enrolment("IR-SA", Seq(EnrolmentIdentifier("UTR", "1234567894")), "Active")))

      stubAuthResult(Future.successful(new ~(saEnrolment, Some(User))))

      val res = testController.getUtr()(FakeRequest())
      status(res) mustBe SEE_OTHER

      verifyDataCached(RegistrationFormData(Identification("1234567894", "AA11 1AA")))
    }

    "redirect to the identify page if the user does not have a UTR enrolment" in {
      stubAuthResult(Future.successful(new ~(Enrolments(Set.empty), Some(User))))

      val res = testController.getUtr()(FakeRequest())
      status(res) mustBe SEE_OTHER

      redirectLocation(res).value mustBe routes.IdentifyController.show().url
    }
  }

  "GET /identify" should {
    "always return 200 Ok and the identify page" in {
      val res = testController.show()(FakeRequest())
      status(res) mustBe OK
      contentAsString(res) must include("Enter your Unique Tax Reference number and postcode")
    }
  }

  "POST /identify" should {
    "return 400 - Bad Request and the identify page when the form data is invalid" in {
      val request = FakeRequest().withFormUrlEncodedBody("utr" -> "", "postcode" -> "")
      val res = testController.validate()(request)

      status(res) mustBe BAD_REQUEST
      contentAsString(res) must include("Enter your Unique Tax Reference number and postcode")
    }

    "redirect to the verify page if the form data is valid" in {
      val request = FakeRequest().withFormUrlEncodedBody("utr" -> "1122334455", "postcode" -> "AA11 1AA")
      val res = testController.validate()(request)

      status(res) mustBe SEE_OTHER
      redirectLocation(res).value mustBe routes.VerifyController.verify().url
    }

    "store the form data in keystore if it is valid" in {
      val request = FakeRequest().withFormUrlEncodedBody("utr" -> "1234567890", "postcode" -> "AA11 1AA")
      val res = testController.validate()(request)

      status(res) mustBe SEE_OTHER

      verify(mockCache, times(1)).cache(
        matching("formData"),
        matching(RegistrationFormData(Identification("1234567890", "AA11 1AA")))
      )(any(), any(), any())
    }
  }

  lazy val testController = wire[IdentifyController]

  def stubAuthResult(res: Future[~[Enrolments, Option[CredentialRole]]]) = {
    when(mockAuthConnector.authorise[~[Enrolments, Option[CredentialRole]]](any(), any())(any(), any())).thenReturn(res)
  }
}
