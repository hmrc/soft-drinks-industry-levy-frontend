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

package sdil.actions

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import play.api.mvc.Results._
import play.api.mvc.{Action, AnyContent}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import sdil.utils.FakeApplicationSpec
import uk.gov.hmrc.auth.core.{Enrolment, EnrolmentIdentifier, Enrolments, MissingBearerToken}

import scala.concurrent.Future

class AuthorisedActionSpec extends FakeApplicationSpec {

  "AuthorisedAction" should {
    "redirect to the gg sign in page if the user is not logged in" in {
      stubAuthResult(Future.failed(MissingBearerToken()))

      val res = testAction(FakeRequest())
      status(res) mustBe SEE_OTHER

      redirectLocation(res).value mustBe ggSignInUrl
    }

    "redirect to the service page if the user is already registered in SDIL" in {
      val sdilEnrolment = EnrolmentIdentifier("EtmpRegistrationNumber", "XZSDIL000100107")
      val enrolments = Enrolments(Set(new Enrolment("HMRC-ORG-OBTDS", Seq(sdilEnrolment), "Active")))

      stubAuthResult(Future.successful(enrolments))

      val res = testAction(FakeRequest())
      status(res) mustBe FORBIDDEN
      contentAsString(res) must include ("You have already registered for this service")
    }

    "invoke the block if the user is logged in and not registered in SDIL" in {
      stubAuthResult(Future.successful(Enrolments(Set.empty)))
      val res = testAction(FakeRequest())

      status(res) mustBe OK
    }

    "invoke the block if the user is enrolled in HMRC-ORG-OBTDS but does not have an SDIL identifier" in {
      val someOtherEnrolment = EnrolmentIdentifier("SomeIdentifier", "SomeValue")
      val enrolments = Enrolments(Set(new Enrolment("HMRC-ORG-OBTDS", Seq(someOtherEnrolment), "Active")))

      stubAuthResult(Future.successful(enrolments))
      val res = testAction(FakeRequest())

      status(res) mustBe OK
    }

    "invoke the block if the user has an ETMP registration, but not an SDIL registration" in {
      val someOtherEtmpEnrolment = EnrolmentIdentifier("EtmpRegistrationNumber", "NotSDIL")
      val enrolments = Enrolments(Set(new Enrolment("HMRC-ORG-OBTDS", Seq(someOtherEtmpEnrolment), "Active")))

      stubAuthResult(Future.successful(enrolments))
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

  def stubAuthResult(res: Future[Enrolments]) = {
    when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any())).thenReturn(res)
  }
}
