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

package sdil.actions

import java.time.LocalDate

import com.softwaremill.macwire.wire
import org.mockito.ArgumentMatchers.{any, anyString, eq => matching}
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import play.api.mvc.Results.Ok
import play.api.mvc.{Action, AnyContent}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import sdil.connectors.SoftDrinksIndustryLevyConnector
import sdil.controllers.ControllerSpec
import sdil.models.backend._
import sdil.models.retrieved.{RetrievedActivity, RetrievedSubscription}
import uk.gov.hmrc.auth.core._

import scala.concurrent.Future

class RegisteredActionSpec extends ControllerSpec with BeforeAndAfterEach {

  lazy val testAuthorisedAction: RegisteredAction = wire[RegisteredAction]
  lazy val testAction: Action[AnyContent] = testAuthorisedAction(_ => Ok)
  val fakeRequest: FakeRequest[AnyContent] = FakeRequest()

  val irCtEnrolment = EnrolmentIdentifier("UTR", "1111111111")
  val enrolments = Enrolments(Set(new Enrolment("IR-CT", Seq(irCtEnrolment), "Active")))

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

  val validDeregisteredRetrievedSubscription =
    validRetrievedSubscription.copy(deregDate = Some(LocalDate.now.plusDays(30)))

  "RegisteredAction" should {
    "redirect to /register/start when no sdil enrolments and no utr" in {
      val agentEnrolment = EnrolmentIdentifier("ARN", "TARN0000011")
      val enrolments = Enrolments(Set(new Enrolment("HMRC-AS-AGENT", Seq(agentEnrolment), "Active")))

      when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any())) thenReturn {
        Future.successful(enrolments)
      }

      when(mockSdilConnectorSPA.retrieveSubscription(matching(irCtEnrolment.value), anyString())(any())).thenReturn {
        Future.successful(Some(validRetrievedSubscription))
      }

      val result = testAction(fakeRequest)

      status(result) mustBe 303
      redirectLocation(result).get mustBe "/soft-drinks-industry-levy/register/start"
    }
    "when no subscription retreived for a given utr" in {
      implicit val fakeRequest: FakeRequest[AnyContent] = FakeRequest()

      when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any())) thenReturn {
        Future.successful(enrolments)
      }

      when(mockSdilConnectorSPA.retrieveSubscription(matching(irCtEnrolment.value), anyString())(any())).thenReturn {
        Future.successful(None)
      }

      val result = testAction(fakeRequest)

      status(result) mustBe 303
      redirectLocation(result).get mustBe "/soft-drinks-industry-levy/register/start" //should then redirect to auth login page
    }

    "Proceed when able to retreive subscrtion for derived UTR" in {
      implicit val fakeRequest: FakeRequest[AnyContent] = FakeRequest()
      when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any())) thenReturn {
        Future.successful(enrolments)
      }
      when(mockSdilConnectorSPA.retrieveSubscription(matching(irCtEnrolment.value), anyString())(any())).thenReturn {
        Future.successful(Some(validRetrievedSubscription))
      }

      val result = testAction(fakeRequest)
      status(result) mustBe 200
    }

    "missing session/bearer token" in {
      implicit val fakeRequest: FakeRequest[AnyContent] = FakeRequest()

      when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any())) thenReturn {
        Future.successful(enrolments)
      }

      when(mockAuthConnector.authorise[Retrieval](any(), any())(any(), any())) thenReturn {
        Future.failed(MissingBearerToken())
      }

      val result = testAction(fakeRequest)

      status(result) mustBe 303
      redirectLocation(result).get mustBe "/soft-drinks-industry-levy/sign-in" //should then redirect to auth login page
    }
  }

  lazy val mockSdilConnectorSPA: SoftDrinksIndustryLevyConnector = {
    val m = mock[SoftDrinksIndustryLevyConnector]
    when(m.submit(any(), any())(any())).thenReturn(Future.successful(()))
    when(m.retrieveSubscription(matching("XKSDIL000000033"), any())(any()))
      .thenReturn(Future.successful(Some(validRetrievedSubscription)))
    when(m.retrieveSubscription(matching("XKSDIL000000036"), any())(any()))
      .thenReturn(Future.successful(Some(validDeregisteredRetrievedSubscription)))
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
