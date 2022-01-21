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
import sdil.models.backend.{Contact, UkAddress}
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

  val mockSdilConnector = mock[SoftDrinksIndustryLevyConnector]
  val mockAppConfig = mock[AppConfig]

  val sampleAddress = Address.fromUkAddress(UkAddress(List("41", "my street", "my town", "my county"), "BN4 4GT"))
  val sampleWarehouse = Warehouse("foo", sampleAddress)
  val sampleSmallProducer = SmallProducer("foo", validSdilRef, (3L, 4L))
  val smallProducer = Producer(true, Some(false))
  val largeProducer = Producer(true, Some(true))

  val sampleLongPair = Some((1L, 2L))

  val samplePeriod: ReturnPeriod = ReturnPeriod(2021, 1)
  val samplePeriods: List[ReturnPeriod] = List(samplePeriod)

  val getReturn: ReturnPeriod => Future[Option[SdilReturn]] = _ => Future.successful(Some(mock[SdilReturn]))

  val submitReturnVariation: ReturnsVariation => Future[Unit] = _ => Future.successful(())
  val broughtForward: BigDecimal = 0
  val sampleCheckSmallProducerStatus: (String, ReturnPeriod) => Future[Option[Boolean]] =
    (_, _) => Future.successful(Some(true))

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

    // TODO - Fix it
    "construct either a RegistrationVariation or a " ignore {
      implicit lazy val ec: ExecutionContext = mock[ExecutionContext]
      implicit lazy val hc: HeaderCarrier = mock[HeaderCarrier]
      implicit lazy val msg: UniformMessages[Html] = mock[UniformMessages[Html]]

      implicit lazy val ltiBoolean: LTInteraction[Unit, Boolean] = mock[LTInteraction[Unit, Boolean]]
      implicit lazy val ltiString: LTInteraction[Unit, String] = mock[LTInteraction[Unit, String]]
      implicit lazy val ltiLongLong: LTInteraction[Unit, Option[(Long, Long)]] =
        mock[LTInteraction[Unit, Option[(Long, Long)]]]
      implicit lazy val ltiLocalDate: LTInteraction[Unit, LocalDate] = mock[LTInteraction[Unit, LocalDate]]
      implicit lazy val ltiContactDetails: LTInteraction[Unit, ContactDetails] =
        mock[LTInteraction[Unit, ContactDetails]]
      implicit lazy val ltiWarehouse: LTInteraction[Unit, Warehouse] =
        mock[LTInteraction[Unit, Warehouse]]
      implicit lazy val ltiContactChangeType: LTInteraction[Unit, Set[VariationsJourney.ContactChangeType]] =
        mock[LTInteraction[Unit, Set[VariationsJourney.ContactChangeType]]]
      implicit lazy val ltiProducerType: LTInteraction[Unit, ProducerType] =
        mock[LTInteraction[Unit, ProducerType]]
      implicit lazy val ltiRepaymentMethod: LTInteraction[Unit, RepaymentMethod] =
        mock[LTInteraction[Unit, RepaymentMethod]]
      implicit lazy val ltiSmallProducer: LTInteraction[Unit, SmallProducer] =
        mock[LTInteraction[Unit, SmallProducer]]
      implicit lazy val ltiReturnPeriod: LTInteraction[Unit, ReturnPeriod] =
        mock[LTInteraction[Unit, ReturnPeriod]]
      implicit lazy val ltiChangeType: LTInteraction[Unit, ChangeType] =
        mock[LTInteraction[Unit, ChangeType]]
      implicit lazy val ltiAbstractChangeType: LTInteraction[Unit, AbstractChangeType] =
        mock[LTInteraction[Unit, AbstractChangeType]]

      //This is the expected result from the journey:
      implicit val sampleListQtyAddress = SampleListQty[Address](10)
      implicit val sampleListQtyWarehouse = SampleListQty[Warehouse](10)
      implicit val sampleListQtySmallProducer = SampleListQty[SmallProducer](5)

      implicit lazy val ltiAddress: LTInteraction[Unit, Address] = mock[LTInteraction[Unit, Address]]
      implicit lazy val ltiAddressBoolean: LTInteraction[Address, Boolean] = mock[LTInteraction[Address, Boolean]]

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

      //val returnsVariationData: RegistrationVariationData = outcome.right
      //returnsVariationData.packingSites.length shouldBe 10
    }

  }

}
