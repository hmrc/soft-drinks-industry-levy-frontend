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
import org.checkerframework.checker.units.qual.s
import org.scalatest.{Matchers, _}
import org.scalatestplus.mockito.MockitoSugar.mock
import play.twirl.api.Html
import sdil.config.AppConfig
import sdil.connectors.SoftDrinksIndustryLevyConnector
import sdil.journeys.VariationsJourney.Change.RegChange
import sdil.models.backend.{Activity, Contact, Subscription, UkAddress}
import sdil.models.retrieved.{RetrievedActivity, RetrievedSubscription}
import sdil.models.variations.{RegistrationVariationData, ReturnVariationData}
import sdil.models.{Address, ContactDetails, Producer, ReturnPeriod, ReturnsVariation, SdilReturn, SmallProducer, Warehouse}
import uk.gov.hmrc.http.HeaderCarrier
import sdil.uniform.SdilComponents._
import sdil.journeys.VariationsJourney._

import java.time.LocalDate
import scala.concurrent.{Await, ExecutionContext, Future, duration}
import duration._

class VariationsJourneySpec extends WordSpec with Matchers {
  implicit val naturalTransformation = new (Future ~> Logic) {
    override def apply[A](fa: Future[A]): Logic[A] =
      Await.result(fa, 30 seconds).pure[Logic]
  }

  val sampleCheckSmallProducerStatus: (String, ReturnPeriod) => Future[Option[Boolean]] =
    (_, _) => Future.successful(Some(true))

  val mockSdilConnector = mock[SoftDrinksIndustryLevyConnector]
  val mockAppConfig = mock[AppConfig]

  val sampleAddress = Address.fromUkAddress(UkAddress(List("41", "my street", "my town", "my county"), "BN4 4GT"))
  val sampleWarehouse = Warehouse("foo", sampleAddress)
  val smallProducer = Producer(true, Some(false))
  val largeProducer = Producer(true, Some(true))

  val sampleLongPair = Some((1L, 2L))

  val samplePeriod: ReturnPeriod = ReturnPeriod(2021, 1)
  val samplePeriods: List[ReturnPeriod] = List(samplePeriod)

  val getReturn: ReturnPeriod => Future[Option[SdilReturn]] = _ => Future.successful(Some(mock[SdilReturn]))
  val returnPeriods = List(ReturnPeriod(2018, 1), ReturnPeriod(2019, 1))
  val emptyreturnPeriods = List()
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
    /*"journey = " in {
      implicit lazy val ec: ExecutionContext = mock[ExecutionContext]
      implicit lazy val hc: HeaderCarrier = mock[HeaderCarrier]
      implicit lazy val msg: UniformMessages[Html] = mock[UniformMessages[Html]]

      implicit lazy val ltiContactChangeType: LTInteraction[Unit, Set[VariationsJourney.ContactChangeType]] =
        mock[LTInteraction[Unit, Set[VariationsJourney.ContactChangeType]]]

      implicit lazy val ltiChangeType: LTInteraction[Unit, ChangeType] =
        mock[LTInteraction[Unit, ChangeType]]

      implicit lazy val ltiAbstractChangeType: LTInteraction[Unit, AbstractChangeType] =
        mock[LTInteraction[Unit, AbstractChangeType]]

      val sampleRepaymentMethod = RepaymentMethod.Credit
      implicit val sampleRepaymentMethodAsk = instances[RepaymentMethod](sampleRepaymentMethod)

      val sampleReturnPeriod = ReturnPeriod(2018, 1)
      implicit val sampleReturnPeriodAsk = instances[ReturnPeriod](sampleReturnPeriod)

      val sampleSmallProducer = SmallProducer("small prod", validSdilRef, (13L, 14L))
      implicit val sampleSmallProducerAsk = instances[SmallProducer](sampleSmallProducer)

      implicit val sampleListQtyAddress = SampleListQty[Address](1)
      val sampleAddress = Address.fromUkAddress(UkAddress(List("41", "my street", "my town", "my county"), "BN4 4GT"))
      implicit val sampleAddressAsk = instances[Address](sampleAddress)

      implicit val sampleListQtyContact = SampleListQty[ContactDetails](1)
      val sampleContact = ContactDetails("fullName", "position", "phoneNumber", "email")
      implicit val sampleContactAsk = instances[ContactDetails](sampleContact)

      implicit val sampleListQtyWarehouse = SampleListQty[Warehouse](1)
      val sampleWarehouse =
        Warehouse("Super Lemonade Plc", Address("1 Warehouse Site St", "Warehouse Site Town", "", "", "AA11 1AA"))
      implicit val sampleWarehouseAsk = instances[Warehouse](sampleWarehouse)

      implicit val sampleListQtyLocalDate = SampleListQty[LocalDate](1)
      val sampleLocalDate =
        LocalDate.ofYearDay(2021, 19)
      implicit val sampleLocalDateAsk = instances[LocalDate](sampleLocalDate)

      val sampleProducer = Producer(true, Some(true))
      implicit val sampleProducerAsk = instances[Producer](sampleProducer)

      val sampleProducerType = ProducerType.Large
      implicit val sampleProducerTypeAsk = instances[ProducerType](sampleProducerType)

      val sampleOrganisationType = OrganisationType.soleTrader
      implicit val sampleOrganisationTypeAsk = instances[OrganisationType](sampleOrganisationType)

      val sampleOrganisationTypeSoleless = OrganisationTypeSoleless.limitedCompany
      implicit val sampleOrganisationTypeSolelessAsk =
        instances[OrganisationTypeSoleless](sampleOrganisationTypeSoleless)

      val sampleLongPair = Some((1L, 2L))
      implicit val sampleLongPairAsk = instances[Option[(Long, Long)]](sampleLongPair)

      implicit val sampleBooleanAsk = instancesF {
        //    case lossKeys(_) => List(false)
        case _ => List(true)
      }

      implicit val sampleStringAsk = instancesF {
        //    case lossKeys(_) => List(false)
        case _ => List("")
      }

      val outcome: Either[ReturnVariationData, RegistrationVariationData] = LogicTableInterpreter
        .interpret(
          VariationsJourney.journey(
            sampleSub,
            validSdilRef,
            samplePeriods,
            samplePeriods,
            mockSdilConnector,
            sampleCheckSmallProducerStatus,
            getReturn,
            submitReturnVariation,
            mockAppConfig
          ))
        .value
        .run
        .asOutcome(true)

      val returnsVariationData: Either[ReturnVariationData, RegistrationVariationData] = outcome
      returnsVariationData shouldBe ???
    }*/

    //Fixed  could not find implicit value for parameter e: ltbs.uniform.interpreters.logictable.SampleListQty[sdil.models.Address]

    implicit val sampleListQtyAddress = SampleListQty[Address](1)
    val sampleAddress = Address.fromUkAddress(UkAddress(List("41", "my street", "my town", "my county"), "BN4 4GT"))
    implicit val sampleAddressAsk = instances[Address](sampleAddress)
    implicit val sampleWarehouseAsk = instances[Warehouse](sampleWarehouse)
    implicit val sampleListQtyWarehouse = SampleListQty[Warehouse](1)

    "activityUpdateJourney" in {
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
      regChange.data.producer shouldBe ???
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
            sampleSub,
            validSdilRef
          ))
        .value
        .run
        .asOutcome(true)

      val regChange = outcome
      regChange.data.producer shouldBe ???
    }
  }
}
