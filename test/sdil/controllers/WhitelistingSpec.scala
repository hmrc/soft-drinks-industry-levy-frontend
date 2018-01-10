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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import play.api.i18n.Messages
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AffinityGroup.Organisation
import uk.gov.hmrc.auth.core.{Enrolment, EnrolmentIdentifier, Enrolments, User}
import uk.gov.hmrc.auth.core.retrieve.~

import scala.concurrent.Future

class WhitelistingSpec extends ControllerSpec {

  "GET /utr" when {
    "whitelisting is enabled" should {
      "prevent users with no UTR enrolment from accessing the service" in {
        testConfig.enableWhitelist("1111111111", "2222222222", "3333333333")

        when(mockAuthConnector.authorise[Retrieval](any(), any())(any(), any())).thenReturn {
          Future.successful(new ~(new ~(new ~(Enrolments(Set.empty), Some(User)), Some("internal id")), Some(Organisation)))
        }

        val res = testController.start()(FakeRequest())

        status(res) mustBe FORBIDDEN
        contentAsString(res) must include (Messages("sdil.not-whitelisted.title"))
      }

      "prevent users with a non-whitelisted UTR from accessing the service" in {
        testConfig.enableWhitelist("1111111111", "2222222222", "3333333333")

        val enrolments = Enrolments(Set(Enrolment("IR-CT", Seq(EnrolmentIdentifier("UTR", "1234567890")), "Active")))

        when(mockAuthConnector.authorise[Retrieval](any(), any())(any(), any())).thenReturn {
          Future.successful(new ~(new ~(new ~(enrolments, Some(User)), Some("internal id")), Some(Organisation)))
        }

        val res = testController.start()(FakeRequest())

        status(res) mustBe FORBIDDEN
        contentAsString(res) must include (Messages("sdil.not-whitelisted.title"))
      }

      "redirect users with a whitelisted UTR to the verify page" in {
        testConfig.enableWhitelist("1111111111", "2222222222", "3333333333")

        val enrolments = Enrolments(Set(Enrolment("IR-CT", Seq(EnrolmentIdentifier("UTR", "1111111111")), "Active")))

        when(mockAuthConnector.authorise[Retrieval](any(), any())(any(), any())).thenReturn {
          Future.successful(new ~(new ~(new ~(enrolments, Some(User)), Some("internal id")), Some(Organisation)))
        }

        val res = testController.start()(FakeRequest())

        status(res) mustBe SEE_OTHER
        redirectLocation(res).value mustBe routes.VerifyController.verify().url
      }
    }

    "whitelisting is disabled" should {
      "redirect users with no UTR enrolment to the identify page" in {
        testConfig.disableWhitelist()

        when(mockAuthConnector.authorise[Retrieval](any(), any())(any(), any())).thenReturn {
          Future.successful(new ~(new ~(new ~(Enrolments(Set.empty), Some(User)), Some("internal id")), Some(Organisation)))
        }

        val res = testController.start()(FakeRequest())
        status(res) mustBe SEE_OTHER
        redirectLocation(res).value mustBe routes.IdentifyController.show().url
      }

      "redirect users with a non-whitelisted UTR to the verify page" in {
        testConfig.disableWhitelist()

        val enrolments = Enrolments(Set(Enrolment("IR-CT", Seq(EnrolmentIdentifier("UTR", "1234567890")), "Active")))

        when(mockAuthConnector.authorise[Retrieval](any(), any())(any(), any())).thenReturn {
          Future.successful(new ~(new ~(new ~(enrolments, Some(User)), Some("internal id")), Some(Organisation)))
        }

        val res = testController.start()(FakeRequest())
        status(res) mustBe SEE_OTHER
        redirectLocation(res).value mustBe routes.VerifyController.verify().url
      }

      "redirect users with a whitelisted UTR to the verify page" in {
        testConfig.disableWhitelist()

        val enrolments = Enrolments(Set(Enrolment("IR-CT", Seq(EnrolmentIdentifier("UTR", "1111111111")), "Active")))

        when(mockAuthConnector.authorise[Retrieval](any(), any())(any(), any())).thenReturn {
          Future.successful(new ~(new ~(new ~(enrolments, Some(User)), Some("internal id")), Some(Organisation)))
        }

        val res = testController.start()(FakeRequest())

        status(res) mustBe SEE_OTHER
        redirectLocation(res).value mustBe routes.VerifyController.verify().url
      }
    }
  }

  "GET /identify" when {
    "whitelisting is enabled" should {
      "prevent users with no UTR enrolment from accessing the service" in {
        testConfig.enableWhitelist("1111111111", "2222222222", "3333333333")

        when(mockAuthConnector.authorise[Retrieval](any(), any())(any(), any())).thenReturn {
          Future.successful(new ~(new ~(new ~(Enrolments(Set.empty), Some(User)), Some("internal id")), Some(Organisation)))
        }

        val res = testController.show()(FakeRequest())

        status(res) mustBe FORBIDDEN
        contentAsString(res) must include (Messages("sdil.not-whitelisted.title"))
      }

      "prevent users with a non-whitelisted UTR from accessing the service" in {
        testConfig.enableWhitelist("1111111111", "2222222222", "3333333333")

        val enrolments = Enrolments(Set(Enrolment("IR-CT", Seq(EnrolmentIdentifier("UTR", "1234567890")), "Active")))

        when(mockAuthConnector.authorise[Retrieval](any(), any())(any(), any())).thenReturn {
          Future.successful(new ~(new ~(new ~(enrolments, Some(User)), Some("internal id")), Some(Organisation)))
        }

        val res = testController.show()(FakeRequest())

        status(res) mustBe FORBIDDEN
        contentAsString(res) must include (Messages("sdil.not-whitelisted.title"))
      }

      "allow users with a whitelisted UTR to see the identify page" in {
        testConfig.enableWhitelist("1111111111", "2222222222", "3333333333")

        val enrolments = Enrolments(Set(Enrolment("IR-CT", Seq(EnrolmentIdentifier("UTR", "1111111111")), "Active")))

        when(mockAuthConnector.authorise[Retrieval](any(), any())(any(), any())).thenReturn {
          Future.successful(new ~(new ~(new ~(enrolments, Some(User)), Some("internal id")), Some(Organisation)))
        }

        val res = testController.show()(FakeRequest())
        status(res) mustBe OK
        contentAsString(res) must include (Messages("sdil.identify.heading"))
      }
    }

    "whitelisting is disabled" should {
      "allow users with no UTR enrolment to see the identify page" in {
        testConfig.disableWhitelist()

        when(mockAuthConnector.authorise[Retrieval](any(), any())(any(), any())).thenReturn {
          Future.successful(new ~(new ~(new ~(Enrolments(Set.empty), Some(User)), Some("internal id")), Some(Organisation)))
        }

        val res = testController.show()(FakeRequest())
        status(res) mustBe OK
        contentAsString(res) must include (Messages("sdil.identify.heading"))
      }

      "allow users with a non-whitelisted UTR to see the identify page" in {
        testConfig.disableWhitelist()

        val enrolments = Enrolments(Set(Enrolment("IR-CT", Seq(EnrolmentIdentifier("UTR", "1234567890")), "Active")))

        when(mockAuthConnector.authorise[Retrieval](any(), any())(any(), any())).thenReturn {
          Future.successful(new ~(new ~(new ~(enrolments, Some(User)), Some("internal id")), Some(Organisation)))
        }

        val res = testController.show()(FakeRequest())
        status(res) mustBe OK
        contentAsString(res) must include (Messages("sdil.identify.heading"))
      }

      "allow users with a whitelisted UTR to see the identify page" in {
        testConfig.disableWhitelist()

        val enrolments = Enrolments(Set(Enrolment("IR-CT", Seq(EnrolmentIdentifier("UTR", "1111111111")), "Active")))

        when(mockAuthConnector.authorise[Retrieval](any(), any())(any(), any())).thenReturn {
          Future.successful(new ~(new ~(new ~(enrolments, Some(User)), Some("internal id")), Some(Organisation)))
        }

        val res = testController.show()(FakeRequest())
        status(res) mustBe OK
        contentAsString(res) must include (Messages("sdil.identify.heading"))
      }
    }
  }

  lazy val testController = wire[IdentifyController]
}
