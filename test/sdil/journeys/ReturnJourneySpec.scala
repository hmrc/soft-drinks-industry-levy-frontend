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
import ltbs.uniform.interpreters.logictable._
import org.scalatest.{Matchers, _}
import sdil.models.backend.{Contact, UkAddress}
import sdil.models.retrieved.{RetrievedActivity, RetrievedSubscription}
import sdil.models.{Address, ReturnPeriod, ReturnsVariation, SdilReturn, SmallProducer, Warehouse}

import scala.concurrent.{Await, Future, duration}, duration._

class ReturnJourneySpec extends WordSpec with Matchers {

  val samplePeriod: ReturnPeriod = ReturnPeriod(2021, 1)
  val sampleReturn: Option[SdilReturn] = None
  val sampleCheckSmallProducerStatus: (String, ReturnPeriod) => Future[Option[Boolean]] =
    (_, _) => Future.successful(Some(true))

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

  val sampleAddress = Address.fromUkAddress(UkAddress(List("41", "my street", "my town", "my county"), "BN4 4GT"))
  val sampleWarehouse = Warehouse("foo", sampleAddress)
  val sampleLongPair = Some((1L, 2L))
  val sampleSmallProducer = SmallProducer("foo", validSdilRef, (3L, 4L))
  // answers
  implicit val sampleAddressAsk = instances[Address](sampleAddress)
  implicit val sampleWarehouseAsk = instances[Warehouse](sampleWarehouse)
  implicit val sampleLongPairAsk = instances[Option[(Long, Long)]](sampleLongPair)
  implicit val sampleSmallProducerAsk = instances[SmallProducer](sampleSmallProducer)
  implicit val sampleListQtySmallProducer = SampleListQty[SmallProducer](1)
  implicit val sampleListQtyAddress = SampleListQty[Address](1)
  implicit val sampleListQtyWarehouse = SampleListQty[Warehouse](1)
  implicit val sampleBooleanAsk = instancesF {
//    case lossKeys(_) => List(false)
    case _ => List(true)
  }

  val submitReturnVariation: ReturnsVariation => Future[Unit] = _ => Future.successful(())
  val broughtForward: BigDecimal = 0

  implicit val naturalTransformation = new (Future ~> Logic) {
    override def apply[A](fa: Future[A]): Logic[A] =
      Await.result(fa, 30 seconds).pure[Logic]
  }

  "returns journey" should {

    "construct SdilReturn and ReturnsVariation for a small producer becoming a copacker" in {
      implicit val sampleListQtyAddress = SampleListQty[Address](10)
      implicit val sampleListQtySmallProducer = SampleListQty[SmallProducer](5)
      val outcome: (SdilReturn, ReturnsVariation) = LogicTableInterpreter
        .interpret(
          ReturnsJourney.journey(
            samplePeriod,
            sampleReturn,
            volManSub,
            sampleCheckSmallProducerStatus,
            submitReturnVariation,
            broughtForward,
            true
          ))
        .value
        .run
        .asOutcome(true)
      // true: To enable logging in unit tests output.
      // false: Please set it to false before merging into github to disablle logging in unit tests output.

      val sdilReturn: SdilReturn = outcome._1
      val returnsVariation: ReturnsVariation = outcome._2

      sdilReturn.totalPacked shouldBe ((16L, 22L))
      returnsVariation.packingSites.length shouldBe 10
    }

    "construct SdilReturn and ReturnsVariation for a given small producer" in {
      implicit val sampleListQtyAddress = SampleListQty[Address](10)
      val sampleSmallProducer = SmallProducer("small prod", validSdilRef, (13L, 14L))
      implicit val sampleSmallProducerAsk = instances[SmallProducer](sampleSmallProducer)

      val outcome: (SdilReturn, ReturnsVariation) = LogicTableInterpreter
        .interpret(
          ReturnsJourney.journey(
            samplePeriod,
            sampleReturn,
            volManSub,
            sampleCheckSmallProducerStatus,
            submitReturnVariation,
            broughtForward,
            true
          ))
        .value
        .run
        .asOutcome(false)
      // true: To enable logging in unit tests output.
      // false: Please set it to false before merging into github to disablle logging in unit tests output.

      val sdilReturn: SdilReturn = outcome._1
      val returnsVariation: ReturnsVariation = outcome._2

      sdilReturn.totalPacked shouldBe ((14L, 16L))
      returnsVariation.packingSites.length shouldBe 10
      returnsVariation.importer._2 shouldBe ((8L, 16L))
    }
  }

}
