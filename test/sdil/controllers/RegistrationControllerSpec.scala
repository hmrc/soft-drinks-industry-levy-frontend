/*
 * Copyright 2024 HM Revenue & Customs
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

import scala.concurrent.ExecutionContext.Implicits.global
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

  "RegistrationController" should {

    "return NOT_FOUND when no subscription" in {

      val sdilEnrolment = EnrolmentIdentifier("EtmpRegistrationNumber", "")
      when(mockAuthConnector.authorise[Enrolments](any(), matching(allEnrolments))(any(), any())).thenReturn {
        Future.successful(Enrolments(Set(Enrolment("HMRC-OBTDS-ORG", Seq(sdilEnrolment), "Active"))))
      }

      val result = controller.index("idvalue").apply(FakeRequest())
      status(result) mustEqual NOT_FOUND
    }

    "start journey when user has subscription and is enrolled" ignore {
      stubCacheEntry(Some(defaultFormData))

      when(mockSdilConnector.retrieveSubscription(matching(irCtEnrolment.value), anyString())(any())).thenReturn {
        Future.successful(Some(aSubscription))
      }

      when(mockSdilConnector.submit(any(), any())(any())) thenReturn Future.successful(())

      val sdilEnrolment = EnrolmentIdentifier("EtmpRegistrationNumber", "XZSDIL000100107")
      when(mockAuthConnector.authorise[Enrolments](any(), matching(allEnrolments))(any(), any())).thenReturn {
        Future.successful(Enrolments(Set(Enrolment("HMRC-OBTDS-ORG", Seq(sdilEnrolment), "Active"))))
      }

      when(mockSdilConnector.retrieveSubscription(matching("XZSDIL000100107"), anyString())(any())).thenReturn {
        Future.successful(Some(aSubscription))
      }

      val result = controller.index("idvalue").apply(FakeRequest())
      status(result) mustEqual SEE_OTHER
      redirectLocation(result) mustEqual Some("organisation-type")
    }

  }
}
