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

package sdil.actions

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import play.api.i18n.Messages
import play.api.mvc.Results._
import play.api.mvc.{Action, AnyContent}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import sdil.utils.FakeApplicationSpec
import uk.gov.hmrc.auth.core.AffinityGroup.{Agent, Organisation}
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.auth.core._

import scala.concurrent.Future

class AuthorisedActionSpec extends FakeApplicationSpec {

  "AuthorisedAction" should {
    "redirect to the gg sign in page if the user is not logged in" in {
      when(mockAuthConnector.authorise[Retrieval](any(), any())(any(), any())) thenReturn {
        Future.failed(MissingBearerToken())
      }

      val res = testAction(FakeRequest())
      status(res) mustBe SEE_OTHER

      redirectLocation(res).value mustBe ggSignInUrl
    }

    "show the 'already registered' error page if the user is already registered in SDIL" in {
      val sdilEnrolment = EnrolmentIdentifier("EtmpRegistrationNumber", "XZSDIL000100107")
      val enrolments = Enrolments(Set(new Enrolment("HMRC-OBTDS-ORG", Seq(sdilEnrolment), "Active")))

      stubAuthResult(new ~(enrolments, Some(User)))

      val res = testAction(FakeRequest())
      status(res) mustBe FORBIDDEN
      contentAsString(res) must include (Messages("sdil.already-enrolled.heading"))
    }

    "show the 'invalid affinity group' error page if the user is an agent" in {
      when(mockAuthConnector.authorise[Retrieval](any(), any())(any(), any())) thenReturn {
        Future.successful(new ~(new ~(new ~(Enrolments(Set.empty), Some(User)), Some("internal id")), Some(Agent)))
      }

      val res = testAction(FakeRequest())
      status(res) mustBe FORBIDDEN
      contentAsString(res) must include (Messages("sdil.invalid-affinity.title"))
    }

    "show the 'invalid role' error page if the user is an assistant" in {
      stubAuthResult(new ~(Enrolments(Set.empty), Some(Assistant)))
      val res = testAction(FakeRequest())

      status(res) mustBe FORBIDDEN
      contentAsString(res) must include (Messages("sdil.invalid-role.title"))
    }

    "invoke the block if the user is logged in and not registered in SDIL" in {
      stubAuthResult(new ~(Enrolments(Set.empty), Some(User)))
      val res = testAction(FakeRequest())

      status(res) mustBe OK
    }

    "invoke the block if the user is enrolled in HMRC-OBTDS-ORG but does not have an SDIL identifier" in {
      val someOtherEnrolment = EnrolmentIdentifier("SomeIdentifier", "SomeValue")
      val enrolments = Enrolments(Set(new Enrolment("HMRC-OBTDS-ORG", Seq(someOtherEnrolment), "Active")))

      stubAuthResult(new ~(enrolments, Some(User)))
      val res = testAction(FakeRequest())

      status(res) mustBe OK
    }

    "invoke the block if the user has an ETMP registration, but not an SDIL registration" in {
      val someOtherEtmpEnrolment = EnrolmentIdentifier("EtmpRegistrationNumber", "NotSDIL")
      val enrolments = Enrolments(Set(new Enrolment("HMRC-OBTDS-ORG", Seq(someOtherEtmpEnrolment), "Active")))

      stubAuthResult(new ~(enrolments, Some(Admin)))
      val res = testAction(FakeRequest())

      status(res) mustBe OK
    }
  }

  lazy val testAuthorisedAction: AuthorisedAction = wire[AuthorisedAction]

  lazy val testAction: Action[AnyContent] = testAuthorisedAction(_ => Ok)

  lazy val ggSignInUrl =
    "http://localhost:9025/gg/sign-in" +
    "?continue=http%3A%2F%2Flocalhost%3A8700%2Fsoft-drinks-industry-levy%2Fregister%2Fidentify" +
    "&origin=soft-drinks-industry-levy-frontend"

  def stubAuthResult(res: => Enrolments ~ Option[CredentialRole]) = {
    when(mockAuthConnector.authorise[Retrieval](any(), any())(any(), any())) thenReturn {
      Future.successful(new ~(new ~(res, Some("internal id")), Some(Organisation)))
    }
  }
}
