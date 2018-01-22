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

import org.jsoup.Jsoup
import org.scalatest.BeforeAndAfterEach
import play.api.test.FakeRequest
import play.api.test.Helpers._
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import uk.gov.hmrc.auth.core.AffinityGroup.Organisation
import uk.gov.hmrc.auth.core.{Admin, Enrolment, EnrolmentIdentifier, Enrolments}
import uk.gov.hmrc.auth.core.retrieve.~

import scala.collection.JavaConverters._
import scala.concurrent.Future

class OrgTypeControllerSpec extends ControllerSpec with BeforeAndAfterEach {

  "OrgType controller" should {
    "always return 200 Ok and the organisation type page" in {
      val request = FakeRequest("GET", "/organisation-type")
      val response = testController.displayOrgType().apply(request)
      status(response) mustBe OK
      contentAsString(response) must include(messagesApi("sdil.organisation-type.heading"))
    }

    "hide the sole trader org type option if the user has a CT-UTR" in {
      lazy val ctUtrEnrolment = Enrolments(Set(Enrolment("IR-CT", Seq(EnrolmentIdentifier("UTR", "9876543210")), "Active")))

      when(mockAuthConnector.authorise[Retrieval](any(), any())(any(), any())) thenReturn {
        Future.successful(new ~(new ~(new ~(ctUtrEnrolment, Some(Admin)), Some("internal id")), Some(Organisation)))
      }

      val res = testController.displayOrgType()(FakeRequest())
      status(res) mustBe OK

      val html = Jsoup.parse(contentAsString(res))
      val options = html.select("""input[name="orgType"]""").asScala.map(_.`val`)

      options must contain theSameElementsAs Seq("limitedCompany", "limitedLiabilityPartnership", "partnership", "unincorporatedBody")
    }

    "return 400 Bad Request if the user selects sole trader, but has a CT-UTR" in {
      lazy val ctUtrEnrolment = Enrolments(Set(Enrolment("IR-CT", Seq(EnrolmentIdentifier("UTR", "9876543210")), "Active")))

      when(mockAuthConnector.authorise[Retrieval](any(), any())(any(), any())) thenReturn {
        Future.successful(new ~(new ~(new ~(ctUtrEnrolment, Some(Admin)), Some("internal id")), Some(Organisation)))
      }

      val res = testController.submitOrgType()(FakeRequest().withFormUrlEncodedBody("orgType" -> "soleTrader"))
      status(res) mustBe BAD_REQUEST
    }

    "return Status: Bad Request for invalid organisation form POST request and show choose option error" in {
      val request = FakeRequest().withFormUrlEncodedBody(
        "orgType" -> "badCompany")
      val response = testController.submitOrgType().apply(request)

      status(response) mustBe BAD_REQUEST
      contentType(response).get mustBe HTML
      contentAsString(response) must include(messagesApi("error.radio-form.choose-option"))
    }

    "return Status: See Other for valid organisation form POST request and redirect to packaging page" in {
      val request = FakeRequest().withFormUrlEncodedBody("orgType" -> "limitedCompany")
      val response = testController.submitOrgType().apply(request)

      status(response) mustBe SEE_OTHER
      redirectLocation(response).get mustBe routes.PackageController.displayPackage().url
    }

    "return Status: See Other for valid organisation as partnership and redirect to partnerships page" in {
      val request = FakeRequest().withFormUrlEncodedBody(
        "orgType" -> "partnership")
      val response = testController.submitOrgType().apply(request)

      status(response) mustBe SEE_OTHER
      redirectLocation(response).get mustBe routes.OrgTypeController.displayPartnerships().url
    }

    "return Status: OK for partnership page with correct title" in {
      val response = testController.displayPartnerships().apply(FakeRequest())

      status(response) mustBe OK
      contentAsString(response) must include(messagesApi("sdil.partnership.heading"))
    }

  }

  lazy val testController = wire[OrgTypeController]

  override protected def beforeEach(): Unit = stubFilledInForm
}

