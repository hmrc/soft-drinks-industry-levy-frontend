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
import org.scalatestplus.mockito.MockitoSugar.mock
import sdil.config.AppConfig
import sdil.connectors.SoftDrinksIndustryLevyConnector
import sdil.journeys.VariationsJourney.Change.RegChange
import sdil.models.backend.{Contact, UkAddress}
import sdil.models.retrieved.{RetrievedActivity, RetrievedSubscription}
import sdil.models.variations.RegistrationVariationData
import sdil.models.{Address, ContactDetails, Producer, ReturnPeriod, ReturnsVariation, SdilReturn, SmallProducer, Warehouse}
import sdil.uniform.SdilComponents._
import sdil.journeys.VariationsJourney._
import sdil.utils.FakeApplicationSpec

import java.time.LocalDate
import scala.concurrent.{Await, Future, duration}
import duration._

class VariationsJourneySpec extends FakeApplicationSpec with Matchers {
  implicit val naturalTransformation = new (Future ~> Logic) {
    override def apply[A](fa: Future[A]): Logic[A] =
      Await.result(fa, 30 seconds).pure[Logic]
  }

  implicit val ufMessages = ltbs.uniform.UniformMessages
  val sampleCheckSmallProducerStatus: (String, ReturnPeriod) => Future[Option[Boolean]] =
    (_, _) => Future.successful(Some(true))

  val mockSdilConnector = mock[SoftDrinksIndustryLevyConnector]
  val mockAppConfig = mock[AppConfig]

  val sampleAddress = Address.fromUkAddress(UkAddress(List("41", "my street", "my town", "my county"), "BN4 4GT"))
  val sampleWarehouse = Warehouse("foo", sampleAddress)
  val smallProducer = Producer(true, Some(false))
  val largeProducer = Producer(true, Some(true))

  val sampleId = "id"
  val sampleLongPair = Some((1L, 2L))
  val samplePeriod: ReturnPeriod = ReturnPeriod(2021, 1)
  val samplePeriods: List[ReturnPeriod] = List(samplePeriod)

  val getReturn: ReturnPeriod => Future[Option[SdilReturn]] = _ => Future.successful(Some(mock[SdilReturn]))
  val returnPeriods = List(ReturnPeriod(2018, 1), ReturnPeriod(2019, 1))
  val emptyreturnPeriods: List[ReturnPeriod] = Nil
  val submitReturnVariation: ReturnsVariation => Future[Unit] = _ => Future.successful(())
  val broughtForward: BigDecimal = 0

  private lazy val validSdilRef = "XCSDIL000000002"
  private lazy val updatedBusinessAddress =
    Address.fromUkAddress(UkAddress(List("32", "new street", "new town", "new county"), "BN5 5GT"))
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
  private lazy val sampleSub = emptySub.copy(
    activity = RetrievedActivity(
      smallProducer = true,
      largeProducer = false,
      contractPacker = false,
      importer = true,
      voluntaryRegistration = false)
  )

  private lazy val registeredUserInformation = RegistrationVariationData(
    emptySub,
    updatedBusinessAddress,
    smallProducer,
    Some(false),
    Some(false),
    None,
    false,
    None,
    false,
    None,
    Seq.empty,
    Seq.empty,
    mock[ContactDetails],
    Seq.empty
  )

  "Variations journey" should {

    implicit val sampleListQtyAddress = SampleListQty[Address](1)
    val sampleAddress = Address.fromUkAddress(UkAddress(List("41", "my street", "my town", "my county"), "BN4 4GT"))
    implicit val sampleAddressAsk = instances[Address](sampleAddress)
    implicit val sampleWarehouseAsk = instances[Warehouse](sampleWarehouse)
    implicit val sampleListQtyWarehouse = SampleListQty[Warehouse](1)

    "activityUpdateJourney update  subscription" in {
      implicit val sampleBooleanAsk = instancesF {
        //    case lossKeys(_) => List(false)
        case _ => List(true)
      }

      implicit val sampleStringAsk = instancesF {
        //    case lossKeys(_) => List(false)
        case _ => List("")
      }

      val sampleProducerType = ProducerType.Large
      implicit val sampleProducerTypeAsk = instances[ProducerType](sampleProducerType)

      val sampleLongPair = Some((1L, 2L))
      implicit val sampleLongPairAsk = instances[Option[(Long, Long)]](sampleLongPair)

      implicit val sampleListQtyLocalDate = SampleListQty[LocalDate](1)
      val sampleLocalDate =
        LocalDate.ofYearDay(2021, 19)
      implicit val sampleLocalDateAsk = instances[LocalDate](sampleLocalDate)

      implicit val sampleListQtyAddress = SampleListQty[Address](10)
      implicit val sampleListQtySmallProducer = SampleListQty[SmallProducer](5)

      val outcome: RegChange = LogicTableInterpreter
        .interpret(
          VariationsJourney.activityUpdateJourney(
            registeredUserInformation,
            emptySub,
            returnPeriods
          ))
        .value
        .run
        .asOutcome(true)

      val regChange = outcome
      regChange.data.original shouldBe emptySub
    }

    "activityUpdateJourney deregister" in {
      implicit val sampleBooleanAsk = instancesF {
        //    case lossKeys(_) => List(false)
        case _ => List(false)
      }

      implicit val sampleStringAsk = instancesF {
        //    case lossKeys(_) => List(false)
        case _ => List("")
      }

      val sampleProducerType = ProducerType.Small
      implicit val sampleProducerTypeAsk = instances[ProducerType](sampleProducerType)

      val sampleLongPair = None
      implicit val sampleLongPairAsk = instances[Option[(Long, Long)]](sampleLongPair)

      implicit val sampleListQtyLocalDate = SampleListQty[LocalDate](1)
      val sampleLocalDate =
        LocalDate.ofYearDay(2021, 19)
      implicit val sampleLocalDateAsk = instances[LocalDate](sampleLocalDate)

      val outcome: RegChange = LogicTableInterpreter
        .interpret(
          VariationsJourney.activityUpdateJourney(
            registeredUserInformation,
            emptySub,
            emptyreturnPeriods
          ))
        .value
        .run
        .asOutcome(true)

      val regChange = outcome
      regChange.data.original shouldBe emptySub
    }

    "changeBusinessAddressJourney" in {
      implicit val sampleBooleanAsk = instancesF {
        //    case lossKeys(_) => List(false)
        case _ => List(true)
      }

      implicit val sampleStringAsk = instancesF {
        //    case lossKeys(_) => List(false)
        case _ => List("")
      }

      implicit val sampleListQtyContact = SampleListQty[ContactDetails](1)
      val sampleContact = ContactDetails("fullName", "position", "phoneNumber", "email")
      implicit val sampleContactAsk = instances[ContactDetails](sampleContact)

      val sampleProducerType = ProducerType.Large
      implicit val sampleProducerTypeAsk = instances[ProducerType](sampleProducerType)

      val sampleLongPair = Some((1L, 2L))
      implicit val sampleLongPairAsk = instances[Option[(Long, Long)]](sampleLongPair)

      implicit val sampleListQtyLocalDate = SampleListQty[LocalDate](1)
      val sampleLocalDate =
        LocalDate.ofYearDay(2021, 19)
      implicit val sampleLocalDateAsk = instances[LocalDate](sampleLocalDate)

      implicit val sampleListQtyAddress = SampleListQty[Address](10)
      implicit val sampleListQtySmallProducer = SampleListQty[SmallProducer](5)

      val sampleContactChangeType = ContactChangeType.ContactPerson
      implicit val SampleContactChangeTypeAsk = instances[ContactChangeType](sampleContactChangeType)

      implicit lazy val ltiContactChangeType: LTInteraction[Unit, Set[VariationsJourney.ContactChangeType]] =
        mock[LTInteraction[Unit, Set[VariationsJourney.ContactChangeType]]]

      val outcome: RegChange = LogicTableInterpreter
        .interpret(
          VariationsJourney.changeBusinessAddressJourney(
            sampleId,
            sampleSub,
            uniformHelpers
          )())
        .value
        .run
        .asOutcome(true)

      val regChange = outcome
      regChange.data.original shouldBe sampleSub
    }
  }
}
