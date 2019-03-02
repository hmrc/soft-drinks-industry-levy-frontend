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

import java.time.LocalDate

import com.softwaremill.macwire._
import org.mockito.ArgumentMatchers.{any, eq => matching}
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterAll
import play.api.i18n.Messages
import play.api.i18n.Messages.Message
import play.api.test.FakeRequest
import play.api.test.Helpers._
import sdil.models.backend._
import sdil.models.retrieved.{RetrievedActivity, RetrievedSubscription}
import uk.gov.hmrc.auth.core.retrieve.Retrievals._
import uk.gov.hmrc.auth.core.{Enrolment, EnrolmentIdentifier, Enrolments}

import scala.concurrent.Future


class ServicePageControllerSpec extends ControllerSpec with BeforeAndAfterAll {

  "Service page controller" should {
    // TODO - we think there may be a bug with the instantiation of objects in macwire? ignoring tests.
    "return Status: OK for displaying service page" ignore {
      val sdilEnrolment = EnrolmentIdentifier("EtmpRegistrationNumber", "XZSDIL000100107")
      when(mockAuthConnector.authorise[Enrolments](any(), matching(allEnrolments))(any(), any())).thenReturn {
        Future.successful(Enrolments(Set(Enrolment("HMRC-OBTDS-ORG", Seq(sdilEnrolment), "Active"))))
      }

      when(mockSdilConnector.retrieveSubscription(matching("XZSDIL000100107"))(any())).thenReturn {
        Future.successful(Some(validRetrievedSubscription))
      }

      val request = FakeRequest("GET", "/home")
      val result = testController.show.apply(request)

      status(result) mustBe OK
      contentAsString(result) must include(messagesApi("sdil.registered.title"))
      contentAsString(result) must include(messagesApi("sdil.service-page.title"))
      contentAsString(result) mustNot include(messagesApi("sdil.common.title"))
      contentAsString(result) mustNot include(messagesApi("sdil.service-page.p2.voluntary-only"))
    }

    "return Status: OK for displaying service page and voluntary information for voluntary registration" ignore {
      val sdilEnrolment = EnrolmentIdentifier("EtmpRegistrationNumber", "XZSDIL000100107")
      when(mockAuthConnector.authorise[Enrolments](any(), matching(allEnrolments))(any(), any())).thenReturn {
        Future.successful(Enrolments(Set(Enrolment("HMRC-OBTDS-ORG", Seq(sdilEnrolment), "Active"))))
      }

      when(mockSdilConnector.retrieveSubscription(matching("XZSDIL000100107"))(any())).thenReturn {
        Future.successful(Some(validVoluntaryRetrievedSubscription))
      }
      val request = FakeRequest("GET", "/home")
      val result = testController.show.apply(request)

      status(result) mustBe OK
      contentAsString(result) must include(messagesApi("sdil.service-page.p2.voluntary-only"))
    }

    "return Status: OK for displaying service page and packaging sites for users who registered them" ignore {
      val sdilEnrolment = EnrolmentIdentifier("EtmpRegistrationNumber", "XZSDIL000100107")
      when(mockAuthConnector.authorise[Enrolments](any(), matching(allEnrolments))(any(), any())).thenReturn {
        Future.successful(Enrolments(Set(Enrolment("HMRC-OBTDS-ORG", Seq(sdilEnrolment), "Active"))))
      }

      when(mockSdilConnector.retrieveSubscription(matching("XZSDIL000100107"))(any())).thenReturn {
        Future.successful(Some(validPackagingRetrievedSubscription))
      }
      val request = FakeRequest("GET", "/home")
      val result = testController.show.apply(request)

      status(result) mustBe OK
      contentAsString(result) must include(messagesApi("sdil.service-page.packaging-address.subtitle"))
      contentAsString(result) mustNot include(messagesApi("sdil.service-page.warehouse-address.subtitle"))
    }

    "return Status: OK for displaying service page and warehouses for users who registered them" ignore {
      val sdilEnrolment = EnrolmentIdentifier("EtmpRegistrationNumber", "XZSDIL000100107")
      when(mockAuthConnector.authorise[Enrolments](any(), matching(allEnrolments))(any(), any())).thenReturn {
        Future.successful(Enrolments(Set(Enrolment("HMRC-OBTDS-ORG", Seq(sdilEnrolment), "Active"))))
      }

      when(mockSdilConnector.retrieveSubscription(matching("XZSDIL000100107"))(any())).thenReturn {
        Future.successful(Some(validWarehouseRetrievedSubscription))
      }
      val request = FakeRequest("GET", "/home")
      val result = testController.show.apply(request)

      status(result) mustBe OK
      contentAsString(result) must include(messagesApi("sdil.service-page.warehouse-address.subtitle"))
      contentAsString(result) mustNot include(messagesApi("sdil.service-page.packaging-address.subtitle"))
    }

    "return Status: NOT_FOUND for displaying service page with no enrolment" ignore {
      val sdilEnrolment = EnrolmentIdentifier("EtmpRegistrationNumber", "XZSDIL000100107")
      when(mockAuthConnector.authorise[Enrolments](any(), matching(allEnrolments))(any(), any())).thenReturn {
        Future.successful(Enrolments(Set(Enrolment("HMRC-OBTDS-ORG", Seq(sdilEnrolment), "Active"))))
      }

      when(mockSdilConnector.retrieveSubscription(matching("XZSDIL000100107"))(any())).thenReturn {
        Future.successful(None)
      }
      val request = FakeRequest().withFormUrlEncodedBody("sdilEnrolment" -> "someValue")
      val result = testController.show.apply(request)

      status(result) mustBe NOT_FOUND
    }
  }

  val validRetrievedSubscription = RetrievedSubscription(
    "111222333",
    "XZSDIL000100107",
    "Cliff's Limonard",
    UkAddress(List("1", "The Road"), "AA11 1AA"),
    RetrievedActivity(false, false, true, false, false),
    LocalDate.of(2018, 4, 6),
    List(Site(UkAddress(List("1 Production Site St", "Production Site Town"), "AA11 1AA"), None, None, None)),
    List(Site(UkAddress(List("1 Warehouse Site St", "Warehouse Site Town"), "AA11 1AA"), None, None, None)),
    Contact(
      Some("A person"),
      Some("A position"),
      "1234",
      "aa@bb.cc")
  )

  val validVoluntaryRetrievedSubscription = validRetrievedSubscription.copy(activity = RetrievedActivity(false, true, true, false, true))

  val validPackagingRetrievedSubscription = validRetrievedSubscription.copy(warehouseSites = Nil)

  val validWarehouseRetrievedSubscription = validRetrievedSubscription.copy(productionSites = Nil)

  lazy val testController: ServicePageController = wire[ServicePageController]
}
