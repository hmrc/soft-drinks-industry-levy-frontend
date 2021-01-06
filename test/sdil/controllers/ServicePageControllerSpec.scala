/*
 * Copyright 2021 HM Revenue & Customs
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
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterAll
import play.api.test.FakeRequest
import play.api.test.Helpers._
import sdil.connectors.SoftDrinksIndustryLevyConnector
import sdil.models.backend._
import sdil.models.retrieved.{RetrievedActivity, RetrievedSubscription}
import sdil.models.{ReturnCharge, ReturnPeriod}
import uk.gov.hmrc.auth.core.AffinityGroup.Organisation
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals._
import uk.gov.hmrc.auth.core.retrieve.~

import java.time.LocalDate
import scala.concurrent.Future

class ServicePageControllerSpec extends ControllerSpec with BeforeAndAfterAll {

  "Service page controller" should {
    "return Status: OK for displaying service page" in {
      val irctEnrolment = Enrolments(Set(Enrolment("IR-CT", Seq(EnrolmentIdentifier("UTR", "0000000033")), "Active")))
      stubAuthResult(new ~(irctEnrolment, Some(User)))

      val sdilEnrolment = EnrolmentIdentifier("EtmpRegistrationNumber", "XKSDIL000000033")
      when(mockAuthConnector.authorise[Enrolments](any(), matching(allEnrolments))(any(), any())).thenReturn {
        Future.successful(Enrolments(Set(Enrolment("HMRC-OBTDS-ORG", Seq(sdilEnrolment), "Active"))))
      }

      when(mockSdilConnectorSPA.retrieveSubscription(matching("XKSDIL000000033"), anyString())(any())).thenReturn {
        Future.successful(Some(validRetrievedSubscription))
      }

      when(mockSdilConnectorSPA.returns_pending(matching("0000000033"))(any()))
        .thenReturn(Future.successful((returnPeriods)))
      when(mockSdilConnectorSPA.returns_get(matching("0000000033"), any[ReturnPeriod]())(any())).thenReturn {
        Future.successful(Some(sdilReturn))
      }

      val request =
        FakeRequest("GET", "/home").withFormUrlEncodedBody("sdilEnrolment" -> "XKSDIL000000033", "utr" -> "0000000033")
      val result = testController.show.apply(request)

      status(result) mustBe OK
      contentAsString(result) must include(messagesApi("sdil.registered.title"))
      contentAsString(result) must include(messagesApi("sdil.service-page.title"))
      contentAsString(result) mustNot include(messagesApi("sdil.common.title"))
      contentAsString(result) mustNot include(messagesApi("sdil.service-page.p2.voluntary-only"))
    }

    "return Status: OK for displaying deregistered service page" in {
      val irctEnrolment = Enrolments(Set(Enrolment("IR-CT", Seq(EnrolmentIdentifier("UTR", "0000000036")), "Active")))
      stubAuthResult(new ~(irctEnrolment, Some(User)))

      val sdilEnrolment = EnrolmentIdentifier("EtmpRegistrationNumber", "XKSDIL000000036")
      when(mockAuthConnector.authorise[Enrolments](any(), matching(allEnrolments))(any(), any())).thenReturn {
        Future.successful(Enrolments(Set(Enrolment("HMRC-OBTDS-ORG", Seq(sdilEnrolment), "Active"))))
      }

      when(mockSdilConnectorSPA.retrieveSubscription(matching("XKSDIL000000036"), anyString())(any())).thenReturn {
        Future.successful(Some(validDeregisteredRetrievedSubscription))
      }

      when(mockSdilConnectorSPA.returns_pending(matching("0000000036"))(any()))
        .thenReturn(Future.successful(List.empty))
      when(mockSdilConnectorSPA.returns_get(matching("0000000036"), any[ReturnPeriod]())(any())).thenReturn {
        Future.successful(Some(sdilReturn))
      }

      val request =
        FakeRequest("GET", "/home").withFormUrlEncodedBody("sdilEnrolment" -> "XKSDIL000000036", "utr" -> "0000000036")
      val result = testController.show.apply(request)

      status(result) mustBe OK
      contentAsString(result) must include(messagesApi("sdil.registered.title"))
      contentAsString(result) must include(messagesApi("sdil.service-page.title"))
      contentAsString(result) must include(messagesApi("returnsHome.dereg.title"))
      contentAsString(result) mustNot include(messagesApi("sdil.common.title"))
      contentAsString(result) mustNot include(messagesApi("sdil.service-page.p2.voluntary-only"))
    }

    "return Status: OK for displaying service page and voluntary information for voluntary registration" in {
      val irctEnrolment = Enrolments(Set(Enrolment("IR-CT", Seq(EnrolmentIdentifier("UTR", "0000000033")), "Active")))
      stubAuthResult(new ~(irctEnrolment, Some(User)))

      val sdilEnrolment = EnrolmentIdentifier("EtmpRegistrationNumber", "XKSDIL000000033")
      when(mockAuthConnector.authorise[Enrolments](any(), matching(allEnrolments))(any(), any())).thenReturn {
        Future.successful(Enrolments(Set(Enrolment("HMRC-OBTDS-ORG", Seq(sdilEnrolment), "Active"))))
      }

      when(mockSdilConnectorSPA.retrieveSubscription(matching("XKSDIL000000033"), anyString())(any())).thenReturn {
        Future.successful(Some(validVoluntaryRetrievedSubscription))
      }

      when(mockSdilConnectorSPA.returns_pending(matching("0000000033"))(any()))
        .thenReturn(Future.successful((returnPeriods)))
      when(mockSdilConnectorSPA.returns_get(matching("0000000033"), any[ReturnPeriod]())(any())).thenReturn {
        Future.successful(Some(sdilReturn))
      }

      val request =
        FakeRequest("GET", "/home").withFormUrlEncodedBody("sdilEnrolment" -> "XKSDIL000000033", "utr" -> "0000000033")
      val result = testController.show.apply(request)

      status(result) mustBe OK

    }

    "return Status: OK for displaying service page and packaging sites for users who registered them" in {
      val irctEnrolment = Enrolments(Set(Enrolment("IR-CT", Seq(EnrolmentIdentifier("UTR", "0000000033")), "Active")))
      stubAuthResult(new ~(irctEnrolment, Some(User)))

      val sdilEnrolment = EnrolmentIdentifier("EtmpRegistrationNumber", "XKSDIL000000033")
      when(mockAuthConnector.authorise[Enrolments](any(), matching(allEnrolments))(any(), any())).thenReturn {
        Future.successful(Enrolments(Set(Enrolment("HMRC-OBTDS-ORG", Seq(sdilEnrolment), "Active"))))
      }

      when(mockSdilConnectorSPA.retrieveSubscription(matching("XKSDIL000000033"), anyString())(any())).thenReturn {
        Future.successful(Some(validPackagingRetrievedSubscription))
      }

      when(mockSdilConnectorSPA.returns_pending(matching("0000000033"))(any()))
        .thenReturn(Future.successful((returnPeriods)))
      when(mockSdilConnectorSPA.returns_get(matching("0000000033"), any[ReturnPeriod]())(any())).thenReturn {
        Future.successful(Some(sdilReturn))
      }

      val request =
        FakeRequest("GET", "/home").withFormUrlEncodedBody("sdilEnrolment" -> "XKSDIL000000033", "utr" -> "0000000033")
      val result = testController.show.apply(request)

      status(result) mustBe OK
      contentAsString(result) mustNot include(messagesApi("sdil.service-page.warehouse-address.subtitle"))
    }

    "return Status: OK for displaying service page and warehouses for users who registered them" in {
      val irctEnrolment = Enrolments(Set(Enrolment("IR-CT", Seq(EnrolmentIdentifier("UTR", "0000000033")), "Active")))
      stubAuthResult(new ~(irctEnrolment, Some(User)))

      val sdilEnrolment = EnrolmentIdentifier("EtmpRegistrationNumber", "XKSDIL000000033")
      when(mockAuthConnector.authorise[Enrolments](any(), matching(allEnrolments))(any(), any())).thenReturn {
        Future.successful(Enrolments(Set(Enrolment("HMRC-OBTDS-ORG", Seq(sdilEnrolment), "Active"))))
      }

      when(mockSdilConnectorSPA.retrieveSubscription(matching("XKSDIL000000033"), anyString())(any())).thenReturn {
        Future.successful(Some(validWarehouseRetrievedSubscription))
      }

      when(mockSdilConnectorSPA.returns_pending(matching("0000000033"))(any()))
        .thenReturn(Future.successful((returnPeriods)))
      when(mockSdilConnectorSPA.returns_get(matching("0000000033"), any[ReturnPeriod]())(any())).thenReturn {
        Future.successful(Some(sdilReturn))
      }

      val request =
        FakeRequest("GET", "/home").withFormUrlEncodedBody("sdilEnrolment" -> "XKSDIL000000033", "utr" -> "0000000033")
      val result = testController.show.apply(request)

      status(result) mustBe OK
      contentAsString(result) mustNot include(messagesApi("sdil.service-page.packaging-address.subtitle"))
    }

    "return Status: NOT_FOUND for displaying service page with no enrolment" in {
      val irctEnrolment = Enrolments(Set(Enrolment("IR-CT", Seq(EnrolmentIdentifier("UTR", "0000000033")), "Active")))
      stubAuthResult(new ~(irctEnrolment, Some(User)))

      val sdilEnrolment = EnrolmentIdentifier("EtmpRegistrationNumber", "XKSDIL000000033")
      when(mockAuthConnector.authorise[Enrolments](any(), matching(allEnrolments))(any(), any())).thenReturn {
        Future.successful(Enrolments(Set(Enrolment("HMRC-OBTDS-ORG", Seq(sdilEnrolment), "Active"))))
      }

      when(mockSdilConnectorSPA.retrieveSubscription(matching("XKSDIL000000033"), anyString())(any())).thenReturn {
        Future.successful(Some(validRetrievedSubscription))
      }

      when(mockSdilConnectorSPA.returns_pending(matching("0000000033"))(any()))
        .thenReturn(Future.successful((returnPeriods)))
      when(mockSdilConnectorSPA.returns_get(matching("0000000033"), any[ReturnPeriod]())(any())).thenReturn {
        Future.successful(Some(sdilReturn))
      }
      val request = FakeRequest().withFormUrlEncodedBody("sdilEnrolment" -> "XKSDIL000000033", "utr" -> "0000000033")
      val result = testController.show.apply(request)

      status(result) mustBe OK
    }

    "check balanceHistory flow" in {
      val irctEnrolment = Enrolments(Set(Enrolment("IR-CT", Seq(EnrolmentIdentifier("UTR", "0000000033")), "Active")))
      stubAuthResult(new ~(irctEnrolment, Some(User)))

      val sdilEnrolment = EnrolmentIdentifier("EtmpRegistrationNumber", "XKSDIL000000033")
      when(mockAuthConnector.authorise[Enrolments](any(), matching(allEnrolments))(any(), any())).thenReturn {
        Future.successful(Enrolments(Set(Enrolment("HMRC-OBTDS-ORG", Seq(sdilEnrolment), "Active"))))
      }

      when(mockSdilConnectorSPA.retrieveSubscription(matching("XKSDIL000000033"), anyString())(any())).thenReturn {
        Future.successful(Some(validRetrievedSubscription))
      }

      when(mockSdilConnectorSPA.returns_pending(matching("0000000033"))(any()))
        .thenReturn(Future.successful((returnPeriods)))
      when(mockSdilConnectorSPA.returns_get(matching("0000000033"), any[ReturnPeriod]())(any())).thenReturn {
        Future.successful(Some(sdilReturn))
      }

      val financiaItem = new ReturnCharge(returnPeriods.head, BigDecimal(12))
      val financialItemList = List(financiaItem)
      when(mockSdilConnectorSPA.balanceHistory(matching("XKSDIL000000033"), anyBoolean())(any()))
        .thenReturn(Future.successful(financialItemList))

      val request = FakeRequest().withFormUrlEncodedBody("sdilEnrolment" -> "XKSDIL000000033", "utr" -> "0000000033")
      val result = testController.balanceHistory.apply(request)

      status(result) mustBe OK
    }

    "show setup direct debit content when they do not have an existing direct debit and when their balance is < 0" in
      new SetupDirectDebitStubs(hasExistingDirectDebit = false, balance = -1) {
        val result = testController.show.apply(request)
        status(result) mustBe OK
        contentAsString(result) must include(messagesApi("direct-debit.manage.section.setup.header"))
        contentAsString(result) must include(messagesApi("direct-debit.manage.section.setup.p1"))
      }

    "show setup direct debit content when they do not have an existing direct debit and when their balance is >= 0" in
      new SetupDirectDebitStubs(hasExistingDirectDebit = false, balance = 1) {
        val result = testController.show.apply(request)
        status(result) mustBe OK
        contentAsString(result) must include(messagesApi("direct-debit.manage.section.setup.header"))
        contentAsString(result) must include(messagesApi("direct-debit.manage.section.setup.p1"))
      }

    "show existing direct debit content when they have an existing direct debit and when their balance is < 0" in
      new SetupDirectDebitStubs(hasExistingDirectDebit = true, balance = -1) {
        val result = testController.show.apply(request)
        status(result) mustBe OK
        contentAsString(result) must include(messagesApi("direct-debit.manage.section.existing.header"))
        contentAsString(result) must include(messagesApi("direct-debit.manage.section.existing.p1"))
        contentAsString(result) must include(messagesApi("direct-debit.manage.section.existing.l1"))
      }

    "show existing direct debit content when they have an existing direct debit and when their balance is >= 0" in
      new SetupDirectDebitStubs(hasExistingDirectDebit = true, balance = 1) {
        val result = testController.show.apply(request)
        status(result) mustBe OK
        contentAsString(result) must include(messagesApi("direct-debit.manage.section.existing.header"))
        contentAsString(result) must include(messagesApi("direct-debit.manage.section.existing.p1"))
        contentAsString(result) must include(messagesApi("direct-debit.manage.section.existing.l1"))
      }

    class SetupDirectDebitStubs(hasExistingDirectDebit: Boolean, balance: BigDecimal) {
      val sdilRef = "XKSDIL000000040"
      val retrievedSubscription = validRetrievedSubscription.copy(sdilRef = sdilRef)

      val sdilEnrolment = EnrolmentIdentifier("EtmpRegistrationNumber", sdilRef)
      when(mockAuthConnector.authorise[Enrolments](any(), matching(allEnrolments))(any(), any())).thenReturn {
        Future.successful(Enrolments(Set(Enrolment("HMRC-OBTDS-ORG", Seq(sdilEnrolment), "Active"))))
      }
      when(mockSdilConnectorSPA.retrieveSubscription(matching(sdilRef), any())(any()))
        .thenReturn(Future.successful(Some(retrievedSubscription)))
      when(mockSdilConnectorSPA.checkDirectDebitStatus(matching(sdilRef))(any()))
        .thenReturn(Future.successful(hasExistingDirectDebit))
      when(mockSdilConnectorSPA.balance(any(), any())(any())).thenReturn(Future.successful(balance))

      val request = FakeRequest().withFormUrlEncodedBody("sdilEnrolment" -> sdilRef, "utr" -> "0000000033")
    }
  }

  val validRetrievedSubscription = RetrievedSubscription(
    "0000000033",
    "XKSDIL000000033",
    "Cliff's Limonard",
    UkAddress(List("1", "The Road"), "AA11 1AA"),
    RetrievedActivity(
      smallProducer = false,
      largeProducer = false,
      contractPacker = true,
      importer = false,
      voluntaryRegistration = false
    ),
    LocalDate.of(2018, 4, 6),
    List(Site(UkAddress(List("1 Production Site St", "Production Site Town"), "AA11 1AA"), None, None, None)),
    List(Site(UkAddress(List("1 Warehouse Site St", "Warehouse Site Town"), "AA11 1AA"), None, None, None)),
    Contact(Some("A person"), Some("A position"), "1234", "aa@bb.cc"),
    None
  )

  def stubAuthResult(res: Enrolments ~ Option[CredentialRole]) =
    when(mockAuthConnector.authorise[Retrieval](any(), any())(any(), any())).thenReturn {
      Future.successful(new ~(new ~(res, Some("internal id")), Some(Organisation)))
    }
  val validDeregisteredRetrievedSubscription =
    validRetrievedSubscription.copy(deregDate = Some(LocalDate.now.plusDays(30)))

  val validVoluntaryRetrievedSubscription =
    validRetrievedSubscription.copy(activity = RetrievedActivity(false, true, true, false, true))

  val validPackagingRetrievedSubscription = validRetrievedSubscription.copy(warehouseSites = Nil)

  val validWarehouseRetrievedSubscription = validRetrievedSubscription.copy(productionSites = Nil)

  lazy val testController: ServicePageController = wire[ServicePageController]

  lazy val mockSdilConnectorSPA: SoftDrinksIndustryLevyConnector = {
    val m = mock[SoftDrinksIndustryLevyConnector]
    when(m.submit(any(), any())(any())).thenReturn(Future.successful(()))
    when(m.retrieveSubscription(matching("XKSDIL000000033"), any())(any()))
      .thenReturn(Future.successful(Some(validRetrievedSubscription)))
    when(m.retrieveSubscription(matching("XKSDIL000000036"), any())(any()))
      .thenReturn(Future.successful(Some(validDeregisteredRetrievedSubscription)))
    when(m.checkDirectDebitStatus(matching("XKSDIL000000033"))(any()))
      .thenReturn(Future.successful(false))
    when(m.checkDirectDebitStatus(matching("XKSDIL000000036"))(any()))
      .thenReturn(Future.successful(false))
    when(m.returns_variable(any())(any())).thenReturn(Future.successful(returnPeriods))
    when(m.returns_vary(any(), any())(any())).thenReturn(Future.successful(()))
    when(m.returns_update(any(), any(), any())(any())).thenReturn(Future.successful(()))
    //when(m.returns_get(any(),any())(any())).thenReturn(Future.successful(None))
    when(m.returns_variation(any(), any())(any())).thenReturn(Future.successful(()))
    when(m.submitVariation(any(), any())(any())).thenReturn(Future.successful(()))
    when(m.balanceHistory(any(), any())(any())).thenReturn(Future.successful(Nil))
    when(m.balance(any(), any())(any())).thenReturn(Future.successful(BigDecimal(0)))
    when(m.shortLiveCache) thenReturn cacheMock
    when(cacheMock.fetchAndGetEntry[Any](any(), any())(any(), any(), any())).thenReturn(Future.successful(None))
    when(m.checkSmallProducerStatus(any(), any())(any())) thenReturn Future.successful(None)

    m
  }

}
