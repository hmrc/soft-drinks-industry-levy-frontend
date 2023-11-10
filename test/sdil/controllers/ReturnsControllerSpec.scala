/*
 * Copyright 2023 HM Revenue & Customs
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

import org.mockito.ArgumentMatchers.{any, eq => matching, _}
import org.mockito.Mockito._
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import sdil.journeys.ReturnsJourney
import sdil.models._
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.allEnrolments
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ReturnsControllerSpec extends ControllerSpec {

  //  "ReturnsController" should {
  //
  //    "redirect to service page when no pending returns for period" in {
  //      val sdilEnrolment = EnrolmentIdentifier("EtmpRegistrationNumber", "XZSDIL000100107")
  //      when(mockAuthConnector.authorise[Enrolments](any(), matching(allEnrolments))(any(), any())).thenReturn {
  //        Future.successful(Enrolments(Set(Enrolment("HMRC-OBTDS-ORG", Seq(sdilEnrolment), "Active"))))
  //      }
  //      when(mockSdilConnector.retrieveSubscription(matching("XCSDIL000000002"), anyString())(any())).thenReturn {
  //        Future.successful(Some(aSubscription))
  //      }
  //      when(mockSdilConnector.returns_pending(matching("0000000022"))(any()))
  //        .thenReturn(Future.successful(List.empty[ReturnPeriod]))
  //
  //      val result = controller.index(2018, 1, nilReturn = false, "idvalue").apply(FakeRequest())
  //      status(result) mustEqual SEE_OTHER
  //      redirectLocation(result) mustEqual Some("/soft-drinks-industry-levy")
  //    }
  //
  //    // TODO: Fix it
  //    "execute index journey" in {
  //      val sdilEnrolment = EnrolmentIdentifier("EtmpRegistrationNumber", "XZSDIL000100107")
  //      when(mockAuthConnector.authorise[Enrolments](any(), matching(allEnrolments))(any(), any())).thenReturn {
  //        Future.successful(Enrolments(Set(Enrolment("HMRC-OBTDS-ORG", Seq(sdilEnrolment), "Active"))))
  //      }
  //      when(mockSdilConnector.retrieveSubscription(matching("XCSDIL000000002"), anyString())(any())).thenReturn {
  //        Future.successful(Some(aSubscription))
  //      }
  //      when(mockSdilConnector.returns_pending(matching("0000000022"))(any()))
  //        .thenReturn(Future.successful(returnPeriods))
  //
  //      val result = controller.index(2018, 1, nilReturn = false, "idvalue").apply(FakeRequest())
  //      status(result) mustEqual SEE_OTHER
  //      redirectLocation(result) mustEqual Some("/soft-drinks-industry-levy") //Some("own-brands-packaged-at-own-sites")
  //    }
  //
  //    "execute index journey and throw a NoSuchElementException" in {
  //      val sdilEnrolment = EnrolmentIdentifier("EtmpRegistrationNumber", "XZSDIL000100107")
  //      when(mockAuthConnector.authorise[Enrolments](any(), matching(allEnrolments))(any(), any())).thenReturn {
  //        Future.successful(Enrolments(Set(Enrolment("HMRC-OBTDS-ORG", Seq(sdilEnrolment), "Active"))))
  //      }
  //      when(mockSdilConnector.retrieveSubscription(matching("XCSDIL000000002"), anyString())(any())).thenReturn {
  //        Future.successful(Some(aSubscription))
  //      }
  //      when(mockSdilConnector.returns_pending(matching("0000000022"))(any()))
  //        .thenThrow(new NoSuchElementException("Exception occurred while retrieving pendingReturns"))
  //
  //      val result = controller.index(2018, 1, nilReturn = false, "idvalue").apply(FakeRequest())
  //      status(result) mustEqual SEE_OTHER
  //
  //    }
  //
  //    "execute index journey with a nil return" in {
  //      val sdilEnrolment = EnrolmentIdentifier("EtmpRegistrationNumber", "XZSDIL000100107")
  //      when(mockAuthConnector.authorise[Enrolments](any(), matching(allEnrolments))(any(), any())).thenReturn {
  //        Future.successful(Enrolments(Set(Enrolment("HMRC-OBTDS-ORG", Seq(sdilEnrolment), "Active"))))
  //      }
  //      when(mockSdilConnector.retrieveSubscription(matching("XCSDIL000000002"), anyString())(any())).thenReturn {
  //        Future.successful(Some(aSubscription))
  //      }
  //      when(mockSdilConnector.returns_pending(matching("0000000022"))(any()))
  //        .thenReturn(Future.successful(returnPeriods))
  //
  //      val result = controller.index(2018, 1, true, "idvalue").apply(FakeRequest())
  //      status(result) mustEqual SEE_OTHER
  //
  //    }
  //
  //    "execute index journey with a nil return and no pending returns" in {
  //      val sdilEnrolment = EnrolmentIdentifier("EtmpRegistrationNumber", "XZSDIL000100107")
  //      when(mockAuthConnector.authorise[Enrolments](any(), matching(allEnrolments))(any(), any())).thenReturn {
  //        Future.successful(Enrolments(Set(Enrolment("HMRC-OBTDS-ORG", Seq(sdilEnrolment), "Active"))))
  //      }
  //      when(mockSdilConnector.retrieveSubscription(matching("XCSDIL000000002"), anyString())(any())).thenReturn {
  //        Future.successful(Some(aSubscription))
  //      }
  //      when(mockSdilConnector.returns_pending(matching("0000000022"))(any())).thenReturn(Future.successful((Nil)))
  //
  //      val result = controller.index(2018, 1, true, "idvalue").apply(FakeRequest())
  //      status(result) mustEqual SEE_OTHER
  //
  //    }
  //
  //    "execute showReturnComplete with" in {
  //      when(mockReturnsCache.get(matching(""))(any()))
  //        .thenReturn(Future.successful(Some(mockreturnFormData)))
  //
  //      val sdilEnrolment = EnrolmentIdentifier("EtmpRegistrationNumber", "XZSDIL000100107")
  //      when(mockAuthConnector.authorise[Enrolments](any(), matching(allEnrolments))(any(), any())).thenReturn {
  //        Future.successful(Enrolments(Set(Enrolment("HMRC-OBTDS-ORG", Seq(sdilEnrolment), "Active"))))
  //      }
  //
  //      when(mockSdilConnector.returns_pending(matching("0000000022"))(any()))
  //        .thenReturn(Future.successful(returnPeriods))
  //
  //      when(mockSdilConnector.retrieveSubscription(matching("XCSDIL000000002"), anyString())(any())).thenReturn {
  //        Future.successful(Some(aSubscription))
  //      }
  //
  //      when(mockSdilConnector.checkSmallProducerStatus(matching("XZSDIL000100107"), any())(any())).thenReturn {
  //        Future.successful(Some(true))
  //      }
  //
  //      val result = controller.showReturnComplete(2018, 1).apply(FakeRequest())
  //      status(result) mustEqual SEE_OTHER
  //    }
  //
  //  }
  lazy val hc: HeaderCarrier = HeaderCarrier()

  "test" should {

    "test" in {

      val jsonObj = Json.parse(
        """
          |[{"date":"2018-09-20","reference":"XNSDIL000100292","amount":10671.84,"lot":"E081800140P0","lotItem":"000001","type":"PaymentOnAccount"},{"date":"2018-10-19","reference":"XNSDIL000100292","amount":19100,"lot":"E081800161P0","lotItem":"000005","type":"PaymentOnAccount"},{"date":"2019-01-10","reference":"XNSDIL000100292","amount":10703.59,"lot":"E081900007P0","lotItem":"000002","type":"PaymentOnAccount"},{"date":"2019-05-03","reference":"XNSDIL000100292","amount":11782.86,"lot":"E081900086P0","lotItem":"000004","type":"PaymentOnAccount"},{"date":"2019-08-19","reference":"XNSDIL000100292","amount":13650,"lot":"E081900160P0","lotItem":"000004","type":"PaymentOnAccount"},{"date":"2019-11-08","reference":"XNSDIL000100292","amount":11280,"lot":"E081900218P0","lotItem":"000005","type":"PaymentOnAccount"},{"date":"2020-01-30","reference":"XNSDIL000100292","amount":8841.54,"lot":"E082000021P0","lotItem":"000031","type":"PaymentOnAccount"},{"date":"2020-05-18","reference":"XNSDIL000100292","amount":8352.49,"lot":"E082000095P0","lotItem":"000003","type":"PaymentOnAccount"},{"date":"2020-07-24","reference":"XNSDIL000100292","amount":14823.78,"lot":"E082000143P0","lotItem":"000002","type":"PaymentOnAccount"},{"date":"2020-09-09","reference":"XNSDIL000100292","amount":417.21,"lot":"E082000175P0","lotItem":"000002","type":"PaymentOnAccount"},{"date":"2020-11-05","reference":"XNSDIL000100292","amount":20760,"lot":"E082000216P0","lotItem":"000004","type":"PaymentOnAccount"},{"date":"2021-04-26","reference":"XNSDIL000100292","amount":511.8,"lot":"E082100207P0","lotItem":"000004","type":"PaymentOnAccount"},{"date":"2021-04-26","reference":"XNSDIL000100292","amount":511.8,"lot":"E082100079P0","lotItem":"000002","type":"PaymentOnAccount"},{"period":{"year":2023,"quarter":0},"amount":-2946.66,"type":"ReturnCharge"},{"date":"2023-05-03","reference":"4300085468","amount":2946.66,"lot":"POL230510003","lotItem":"000007","type":"PaymentOnAccount"},{"date":"2023-06-23","title":"SDIL Late Filing Penalty","amount":-100,"type":"Unknown"},{"date":"2023-07-21","title":"SDIL Late Payment Penalty","amount":-147.33,"type":"Unknown"}]
          |""".stripMargin)

      val items: List[FinancialLineItem] = jsonObj.as[List[FinancialLineItem]]

      val bForward = extractTotal(listItemsWithTotal(items))

      println(Console.BLUE + "brought forward: " + bForward + Console.RESET)

      val rd = SdilReturn(
        ownBrand = (0,0),
        packLarge = (2623500, 706457),
        packSmall = List(SmallProducer("", "", (13781, 100954))),
        importLarge = (0,0),
        importSmall = (0,0),
        export = (1268294, 185555),
        wastage = (0,0),
        submittedOn = None
      )

      val data = ReturnsJourney.returnAmount(rd, false)

      val subtotal = ReturnsJourney.calculateSubtotal(data)

      println(Console.BLUE + "subtotal: " + subtotal + Console.RESET)

      val total = subtotal - bForward

      println(Console.BLUE + "total: " + total + Console.RESET)

      total mustEqual 1
    }

  }
  val controller = new ReturnsController(
    stubMessagesControllerComponents,
    testConfig,
    uniformHelpers,
    registeredAction,
    mockSdilConnector,
    mockCache,
    mockReturnsCache
  )
}
