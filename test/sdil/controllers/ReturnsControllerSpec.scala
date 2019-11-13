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

import cats.implicits._
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import play.api.libs.json._
import play.api.mvc._
import uk.gov.hmrc.http.{CoreDelete, CoreGet, CorePut, HeaderCarrier}
import com.softwaremill.macwire._
import play.api.test.FakeRequest
import uk.gov.hmrc.http.cache.client.ShortLivedHttpCaching

import scala.concurrent._
import duration._
import org.scalatest.MustMatchers._
import com.softwaremill.macwire._
import org.mockito.ArgumentMatchers.{any, eq => matching}
import org.mockito.Mockito._
import play.api.i18n.Messages
import play.api.test.FakeRequest
import play.api.test.Helpers._
import sdil.config.SDILShortLivedCaching
import sdil.models._
import backend._
import retrieved._
import uk.gov.hmrc.auth.core.AffinityGroup.Organisation
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.~
import play.api.libs.json._
import uk.gov.hmrc.auth.core.retrieve.Retrievals.allEnrolments
import uk.gov.hmrc.uniform.webmonad.WebMonad

import scala.concurrent.Future

class ReturnsControllerSpec extends ControllerSpec {

  "ReturnsController" should {

    "execute main program" in {
      def subProgram = controller.program(validReturnPeriod, emptySub, validSdilRef, false, validId)(hc)

      val output = controllerTester.testJourney(subProgram)(
        "own-brands-packaged-at-own-sites"     -> Json.obj("lower" -> 123234, "higher" -> 2340000),
        "packaged-as-a-contract-packer"        -> Json.obj("lower" -> 1234579, "higher" -> 2345679),
        "_editSmallProducers"                  -> Json.toJson(true),
        "exemptions-for-small-producers"       -> Json.toJson(false),
        "small-producer-details"               -> JsString("Done"),
        "brought-into-uk"                      -> Json.obj("lower" -> 1234562, "higher" -> 2345672),
        "brought-into-uk-from-small-producers" -> Json.obj("lower" -> 1234, "higher" -> 2345),
        "claim-credits-for-exports"            -> Json.obj("lower" -> 6789, "higher" -> 2345),
        "claim-credits-for-lost-damaged"       -> Json.obj("lower" -> 123, "higher" -> 234),
        "return-change-registration"           -> JsNull,
        "ask-secondary-warehouses-in-return"   -> Json.toJson(true),
        "pack-at-business-address-in-return"   -> Json.toJson(false),
        "first-production-site" -> Json.obj(
          "address" -> Json.obj("lines" -> List("117 Jerusalem Courtz", "St Albans"), "postCode" -> "AL10 3UJ")),
        "production-site-details_data" -> JsArray(
          List(
            Json.obj(
              "address"        -> Json.obj("lines" -> List("117 Jerusalem Courtz", "St Albans"), "postCode" -> "AL10 3UJ")),
            Json.obj("address" -> Json.obj("lines" -> List("12 The Street", "Blahdy Corner"), "postCode"    -> "AB12 3CD"))
          ))
      )
      status(output) mustBe SEE_OTHER
    }

    "execute main program as a vol man user" in {
      def subProgram: WebMonad[Result] =
        controller.program(validReturnPeriod, volManSub, validSdilRef, false, validId)(hc)

      fetchAndGet(smallprod)

      val output = controllerTester.testJourney(subProgram)(
        "own-brands-packaged-at-own-sites"     -> Json.obj("lower" -> 123234, "higher" -> 2340000),
        "packaged-as-a-contract-packer"        -> Json.obj("lower" -> 1234579, "higher" -> 2345679),
        "_editSmallProducers"                  -> Json.toJson(true),
        "exemptions-for-small-producers"       -> Json.toJson(false),
        "small-producer-details"               -> JsString("Done"),
        "brought-into-uk"                      -> Json.obj("lower" -> 1234562, "higher" -> 2345672),
        "brought-into-uk-from-small-producers" -> Json.obj("lower" -> 1234, "higher" -> 2345),
        "claim-credits-for-exports"            -> Json.obj("lower" -> 6789, "higher" -> 2345),
        "claim-credits-for-lost-damaged"       -> Json.obj("lower" -> 123, "higher" -> 234),
        "return-change-registration"           -> JsNull,
        "ask-secondary-warehouses-in-return"   -> Json.toJson(true),
        "pack-at-business-address-in-return"   -> Json.toJson(false),
        "first-production-site" -> Json.obj(
          "address" -> Json.obj("lines" -> List("117 Jerusalem Courtz", "St Albans"), "postCode" -> "AL10 3UJ")),
        "production-site-details_data" -> JsArray(
          List(
            Json.obj(
              "address"        -> Json.obj("lines" -> List("117 Jerusalem Courtz", "St Albans"), "postCode" -> "AL10 3UJ")),
            Json.obj("address" -> Json.obj("lines" -> List("12 The Street", "Blahdy Corner"), "postCode"    -> "AB12 3CD"))
          ))
      )
      status(output) mustBe SEE_OTHER
    }

    "execute main program as user who becomes mandatory and add packaging sites" in {
      def subProgram: WebMonad[Result] =
        controller.program(validReturnPeriod, emptySub, validSdilRef, false, validId)(hc)

      val total: BigDecimal = 200000.12
      val dummyLineItem: FinancialLineItem = CentralAssessment(LocalDate.of(2019, 1, 1), total)

      fetchAndGet(smallprod)
      balanceHistory(List(dummyLineItem))
      balance(total)
      checkSmallProdStatus(true)

      val output = controllerTester.testJourney(subProgram)(
        "own-brands-packaged-at-own-sites"     -> Json.obj("lower" -> 123234, "higher" -> 2340000),
        "packaged-as-a-contract-packer"        -> Json.obj("lower" -> 1234579, "higher" -> 2345679),
        "_editSmallProducers"                  -> Json.toJson(true),
        "exemptions-for-small-producers"       -> Json.toJson(false),
        "small-producer-details"               -> JsString("Done"),
        "brought-into-uk"                      -> Json.obj("lower" -> 1234562, "higher" -> 2345672),
        "brought-into-uk-from-small-producers" -> Json.obj("lower" -> 1234, "higher" -> 2345),
        "claim-credits-for-exports"            -> Json.obj("lower" -> 6789, "higher" -> 2345),
        "claim-credits-for-lost-damaged"       -> Json.obj("lower" -> 123, "higher" -> 234),
        "return-change-registration"           -> Json.toJson(true),
        "pack-at-business-address-in-return"   -> Json.toJson(true),
        "first-production-site" -> Json.obj(
          "address" -> Json.obj("lines" -> List("117 Jerusalem Courtz", "St Albans"), "postCode" -> "AL10 3UJ")),
        "production-site-details_data" -> JsArray(
          List(
            Json.obj(
              "address"        -> Json.obj("lines" -> List("117 Jerusalem Courtz", "St Albans"), "postCode" -> "AL10 3UJ")),
            Json.obj("address" -> Json.obj("lines" -> List("12 The Street", "Blahdy Corner"), "postCode"    -> "AB12 3CD"))
          )),
        "production-site-details"            -> JsString("Done"),
        "addWarehouses"                      -> Json.toJson(true),
        "ask-secondary-warehouses-in-return" -> Json.toJson(false),
        "secondary-warehouse-details"        -> JsString("Done")
        //        "first-warehouse"   -> Json.toJson(true),
        //        "secondary-warehouses-details_data" -> JsArray(List(
        //          Json.obj("address" -> Json.obj("lines" -> List("117 Jerusalem Courtz","St Albans"),"postCode" -> "AL10 3UJ")),
        //          Json.obj("address" -> Json.obj("lines" -> List("12 The Street","Blahdy Corner"),"postCode" -> "AB12 3CD"))
        //        ))
      )
      status(output) mustBe SEE_OTHER
    }

    "show the confirmation page at the end of the journey" in {
      val output: WebMonad[Result] = controller.confirmationPage(
        "return-sent",
        validReturnPeriod,
        emptySub,
        emptyReturn,
        BigDecimal(0),
        validSdilRef,
        true,
        emptyReturnsVariation)

      val program = controllerTester.testJourney(output)("blah" -> JsNull)

      status(program) mustBe OK
    }

    "show the confirmation page at the end of the journey with credit carried forward" in {
      val output: WebMonad[Result] = controller.confirmationPage(
        "return-sent",
        validReturnPeriod,
        emptySub,
        emptyReturn,
        BigDecimal(100),
        validSdilRef,
        true,
        emptyReturnsVariation)

      val program = controllerTester.testJourney(output)("blah" -> JsNull)

      status(program) mustBe OK
    }

    "show the confirmation page at the end of the journey with debit carried forward" in {
      val output: WebMonad[Result] = controller.confirmationPage(
        "return-sent",
        validReturnPeriod,
        emptySub,
        emptyReturn,
        BigDecimal(-100),
        validSdilRef,
        true,
        emptyReturnsVariation)

      val program = controllerTester.testJourney(output)("blah" -> JsNull)

      status(program) mustBe OK
    }

    "execute index journey" in {
      val sdilEnrolment = EnrolmentIdentifier("EtmpRegistrationNumber", "XZSDIL000100107")
      when(mockAuthConnector.authorise[Enrolments](any(), matching(allEnrolments))(any(), any())).thenReturn {
        Future.successful(Enrolments(Set(Enrolment("HMRC-OBTDS-ORG", Seq(sdilEnrolment), "Active"))))
      }
      when(mockSdilConnector.retrieveSubscription(matching("XCSDIL000000002"), anyString())(any())).thenReturn {
        Future.successful(Some(aSubscription))
      }
      when(mockSdilConnector.returns_pending(matching("0000000022"))(any()))
        .thenReturn(Future.successful(returnPeriods))

      val result = controller.index(2018, 1, nilReturn = false, "idvalue").apply(FakeRequest())
      status(result) mustEqual SEE_OTHER

    }

    "execute index journey with a nil return" in {
      val sdilEnrolment = EnrolmentIdentifier("EtmpRegistrationNumber", "XZSDIL000100107")
      when(mockAuthConnector.authorise[Enrolments](any(), matching(allEnrolments))(any(), any())).thenReturn {
        Future.successful(Enrolments(Set(Enrolment("HMRC-OBTDS-ORG", Seq(sdilEnrolment), "Active"))))
      }
      when(mockSdilConnector.retrieveSubscription(matching("XCSDIL000000002"), anyString())(any())).thenReturn {
        Future.successful(Some(aSubscription))
      }
      when(mockSdilConnector.returns_pending(matching("0000000022"))(any()))
        .thenReturn(Future.successful(returnPeriods))

      val result = controller.index(2018, 1, true, "idvalue").apply(FakeRequest())
      status(result) mustEqual SEE_OTHER

    }

    "execute index journey with a nil return and no pending returns" in {
      val sdilEnrolment = EnrolmentIdentifier("EtmpRegistrationNumber", "XZSDIL000100107")
      when(mockAuthConnector.authorise[Enrolments](any(), matching(allEnrolments))(any(), any())).thenReturn {
        Future.successful(Enrolments(Set(Enrolment("HMRC-OBTDS-ORG", Seq(sdilEnrolment), "Active"))))
      }
      when(mockSdilConnector.retrieveSubscription(matching("XCSDIL000000002"), anyString())(any())).thenReturn {
        Future.successful(Some(aSubscription))
      }
      when(mockSdilConnector.returns_pending(matching("0000000022"))(any())).thenReturn(Future.successful((Nil)))

      val result = controller.index(2018, 1, true, "idvalue").apply(FakeRequest())
      status(result) mustEqual SEE_OTHER

    }

    "execute main program as a vol man user with secondary warehouses" in {
      def subProgram: WebMonad[Result] =
        controller.program(validReturnPeriod, volManSub, validSdilRef, false, validId)(hc)

      fetchAndGet(smallprod)

      val output = controllerTester.testJourney(subProgram)(
        "own-brands-packaged-at-own-sites"     -> Json.obj("lower" -> 123234, "higher" -> 2340000),
        "packaged-as-a-contract-packer"        -> Json.obj("lower" -> 1234579, "higher" -> 2345679),
        "_editSmallProducers"                  -> Json.toJson(true),
        "exemptions-for-small-producers"       -> Json.toJson(false),
        "small-producer-details"               -> JsString("Done"),
        "brought-into-uk"                      -> Json.obj("lower" -> 1234562, "higher" -> 2345672),
        "brought-into-uk-from-small-producers" -> Json.obj("lower" -> 1234, "higher" -> 2345),
        "claim-credits-for-exports"            -> Json.obj("lower" -> 6789, "higher" -> 2345),
        "claim-credits-for-lost-damaged"       -> Json.obj("lower" -> 123, "higher" -> 234),
        "return-change-registration"           -> JsNull,
        "ask-secondary-warehouses-in-return"   -> Json.toJson(true),
        "pack-at-business-address-in-return"   -> Json.toJson(false),
        "first-warehouse" -> Json.obj(
          "address" -> Json.obj("lines" -> List("117 Jerusalem Courtz", "St Albans"), "postCode" -> "AL10 3UJ")),
        "first-production-site" -> Json.obj(
          "address" -> Json.obj("lines" -> List("117 Jerusalem Courtz", "St Albans"), "postCode" -> "AL10 3UJ")),
        "production-site-details_data" -> JsArray(
          List(
            Json.obj(
              "address"        -> Json.obj("lines" -> List("117 Jerusalem Courtz", "St Albans"), "postCode" -> "AL10 3UJ")),
            Json.obj("address" -> Json.obj("lines" -> List("12 The Street", "Blahdy Corner"), "postCode"    -> "AB12 3CD"))
          ))
      )
      status(output) mustBe SEE_OTHER
    }
  }

  lazy val controller: ReturnsController = wire[ReturnsController]
  lazy val controllerTester = new UniformControllerTester(controller)

  lazy val shortLivedCaching: ShortLivedHttpCaching = new ShortLivedHttpCaching {
    override def baseUri: String = ???
    override def domain: String = ???
    override def defaultSource: String = ???
    override def http: CoreGet with CorePut with CoreDelete = ???
  }
  lazy val hc: HeaderCarrier = HeaderCarrier()

  private lazy val validReturnPeriod = ReturnPeriod(2018, 1)
  private lazy val emptySub = RetrievedSubscription(
    "0000000022",
    "",
    "",
    UkAddress(Nil, ""),
    RetrievedActivity(
      smallProducer = false,
      largeProducer = false,
      contractPacker = false,
      importer = false,
      voluntaryRegistration = false),
    java.time.LocalDate.now,
    Nil,
    Nil,
    Contact(None, None, "", "")
  )
  private lazy val validSdilRef = "XCSDIL000000002"
  private lazy val validId = "start"
  private lazy val volManSub = emptySub.copy(
    activity = RetrievedActivity(
      smallProducer = true,
      largeProducer = false,
      contractPacker = false,
      importer = true,
      voluntaryRegistration = false)
  )
  private lazy val smallprod = Json.obj("smallProd" -> "here I am")

  private lazy val emptyReturn = SdilReturn((0, 0), (0, 0), List.empty, (0, 0), (0, 0), (0, 0), (0, 0))

  private lazy val emptyReturnsVariation = ReturnsVariation(
    "someOrg",
    UkAddress(Nil, "AA111AA"),
    (false, (0, 0)),
    (false, (0, 0)),
    Nil,
    Nil,
    "07830440425",
    "legitemail@legitdomain.com",
    BigDecimal(0))
  // DATA OUT:Map(claim-credits-for-exports -> {"lower":6789,"higher":2345}, packaged-as-a-contract-packer -> {"lower":1234579,"higher":2345679}, claim-credits-for-lost-damaged -> {"lower":123,"higher":234}, brought-into-uk-from-small-producers -> {"lower":1234,"higher":2345}, _editSmallProducers -> false, own-brands-packaged-at-own-sites -> {"lower":123234,"higher":2340000}, small-producer-details -> "Done", return-change-registration -> null, brought-into-uk -> {"lower":1234562,"higher":2345672}, ask-secondary-warehouses-in-return -> false, exemptions-for-small-producers -> false)

}
