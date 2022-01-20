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

package sdil.journeys
import cats.implicits._
import cats.~>
import ltbs.uniform.UniformMessages
import ltbs.uniform.interpreters.logictable._
import org.scalatest.{Matchers, _}
import org.scalatestplus.mockito.MockitoSugar.mock
import play.twirl.api.Html
import sdil.config.AppConfig
import sdil.connectors.SoftDrinksIndustryLevyConnector
import sdil.models.backend.{Contact, UkAddress}
import sdil.models.retrieved.{RetrievedActivity, RetrievedSubscription}
import sdil.models.variations.RegistrationVariationData
import sdil.models.{Address, ReturnPeriod, ReturnsVariation, SdilReturn, SmallProducer, Warehouse}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{Await, ExecutionContext, Future, duration}
import duration._

//class VariationsJourneySpec extends WordSpec with Matchers {
//  implicit val naturalTransformation = new (Future ~> Logic) {
//    override def apply[A](fa: Future[A]): Logic[A] =
//      Await.result(fa, 30 seconds).pure[Logic]
//  }
//
//  val mockSdilConnector = mock[SoftDrinksIndustryLevyConnector]
//  val mockAppConfig = mock[AppConfig]
//
//  val sampleAddress = Address.fromUkAddress(UkAddress(List("41", "my street", "my town", "my county"), "BN4 4GT"))
//  val sampleWarehouse = Warehouse("foo", sampleAddress)
//  val sampleSmallProducer = SmallProducer("foo", validSdilRef, (3L, 4L))
//  val sampleLongPair = Some((1L, 2L))
//
//  val samplePeriod: ReturnPeriod = ReturnPeriod(2021, 1)
//  val samplePeriods: List[ReturnPeriod] = List(samplePeriod)
//
//  val getReturn: ReturnPeriod => Future[Option[SdilReturn]] = _ => Future.successful(())
//  val submitReturnVariation: ReturnsVariation => Future[Unit] = _ => Future.successful(())
//  val broughtForward: BigDecimal = 0
//  val sampleCheckSmallProducerStatus: (String, ReturnPeriod) => Future[Option[Boolean]] =
//    (_, _) => Future.successful(Some(true))
//
//  private lazy val validSdilRef = "XCSDIL000000002"
//  private lazy val updatedBusinessAddress =
//    Address.fromUkAddress(UkAddress(List("32", "new street", "new town", "new county"), "BN5 5GT"))
//  private lazy val emptySub = RetrievedSubscription(
//    "0000000022",
//    "",
//    "",
//    UkAddress(Nil, ""),
//    RetrievedActivity(
//      smallProducer = false,
//      largeProducer = false,
//      contractPacker = false,
//      importer = false,
//      voluntaryRegistration = false),
//    java.time.LocalDate.now,
//    Nil,
//    Nil,
//    Contact(None, None, "", "")
//  )
//  private lazy val sampleSub = emptySub.copy(
//    activity = RetrievedActivity(
//      smallProducer = true,
//      largeProducer = false,
//      contractPacker = false,
//      importer = true,
//      voluntaryRegistration = false)
//  )
//  private lazy val RegisteredUserInformation = RegistrationVariationData(
//    emptySub,
//    updatedBusinessAddress,
//    sampleSmallProducer,
//    false,
//    false,
//    None,
//    false,
//    None,
//    false,
//    None
//  )
//
//  "variations journey" should {
//    "construct either a RegistrationVariation or a " in {
//      //This is the expected result from the journey:
//      implicit val sampleListQtyAddress = SampleListQty[Address](10)
//      implicit val sampleListQtySmallProducer = SampleListQty[SmallProducer](5)
//      val outcome: (SdilReturn, ReturnsVariation) = LogicTableInterpreter
//        .interpret(
//          VariationsJourney.journey(
//            sampleSub,
//            validSdilRef,
//            samplePeriods,
//            samplePeriods,
//            mockSdilConnector,
//            sampleCheckSmallProducerStatus,
//            getReturn,
//            submitReturnVariation,
//            mockAppConfig
//          )(_: ExecutionContext, _: HeaderCarrier, _: UniformMessages[Html]))
//        .value
//        .run
//        .asOutcome(true)
//
//      val returnsVariation: ReturnsVariation = outcome._2
//      returnsVariation.packingSites.length shouldBe 10
//    }
//  }
//
//}
