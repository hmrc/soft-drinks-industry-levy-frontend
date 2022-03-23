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
import ltbs.uniform.interpreters.logictable.{Logic, LogicTableInterpreter, SampleListQty}
import org.mockito.Mockito.mock
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.twirl.api.Html
import sdil.controllers.{ControllerSpec, RegistrationController}
import sdil.models.{Address, ContactDetails, DetailsCorrect, Litreage, OrganisationDetails, Producer, RegistrationFormData, RosmRegistration, Warehouse}
import sdil.models.backend.{Site, Subscription, UkAddress}
import sdil.uniform.SdilComponents.{OrganisationType, OrganisationTypeSoleless, ProducerType}

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{Await, ExecutionContext, Future, duration}
import duration._
import scala.concurrent.ExecutionContext.Implicits.global

class RegistrationJourneySpec extends AnyWordSpecLike with Matchers {

  lazy val defaultRosmData: RosmRegistration = RosmRegistration(
    "some-safe-id",
    Some(
      OrganisationDetails(
        "an organisation"
      )),
    None,
    Address("1", "The Road", "", "", "AA11 1AA")
  )

  lazy val defaultFormData: RegistrationFormData = {
    RegistrationFormData(
      rosmData = defaultRosmData,
      utr = "1234567890",
      verify = Some(DetailsCorrect.Yes),
      organisationType = Some("partnership"),
      producer = Some(Producer(isProducer = true, isLarge = Some(false))),
      isPackagingForSelf = Some(true),
      volumeForOwnBrand = Some(
        Litreage(
          atLowRate = 1,
          atHighRate = 2
        )),
      packagesForOthers = Some(true),
      volumeForCustomerBrands = Some(
        Litreage(
          atLowRate = 3,
          atHighRate = 4
        )),
      usesCopacker = Some(true),
      isImporter = Some(true),
      importVolume = Some(
        Litreage(
          atLowRate = 9,
          atHighRate = 10
        )),
      startDate = Some(LocalDate.of(2018, 4, 6)),
      productionSites = Some(
        Seq(
          Site.fromAddress(Address("1 Production Site St", "Production Site Town", "", "", "AA11 1AA"))
        )),
      secondaryWarehouses = Some(
        Seq(
          Site.fromAddress(Address("1 Warehouse Site St", "Warehouse Site Town", "", "", "AA11 1AA"))
        )),
      contactDetails = Some(
        ContactDetails(
          fullName = "A person",
          position = "A position",
          phoneNumber = "1234",
          email = "aa@bb.cc"
        ))
    )
  }

  "RegistrationJourney" should {

    "construct a subscription" ignore {

      implicit val messages: UniformMessages[Html] = new UniformMessages[Html] {
        override def get(key: String, args: Any*): Option[Html] = Some(Html("You do not need to register"))
        override def list(key: String, args: Any*): List[Html] = List(Html("You do not need to register"))
      }

      def backendCall(s: Subscription): Future[Unit] = Future.successful(s)

      //NO UK
      implicit val sampleListQtyAddress = SampleListQty[Address](0)
      val sampleAddress = Address.fromUkAddress(UkAddress(List(), ""))
      implicit val sampleAddressAsk = instances[Address](sampleAddress)

      //1 CONTACT
      implicit val sampleListQtyContact = SampleListQty[ContactDetails](1)
      val sampleContact = ContactDetails("fullName", "position", "phoneNumber", "email")
      implicit val sampleContactAsk = instances[ContactDetails](sampleContact)

      //GIVING 1 WAREHOUSE
      implicit val sampleListQtyWarehouse = SampleListQty[Warehouse](1)
      val sampleWarehouse =
        Warehouse("Super Lemonade Plc", Address("1 Warehouse Site St", "Warehouse Site Town", "", "", "AA11 1AA"))
      implicit val sampleWarehouseAsk = instances[Warehouse](sampleWarehouse)

      //LOCAL DATE
      implicit val sampleListQtyLocalDate = SampleListQty[LocalDate](1)
      val sampleLocalDate =
        LocalDate.ofYearDay(2021, 19)
      implicit val sampleLocalDateAsk = instances[LocalDate](sampleLocalDate)

      //isProducer: Boolean, isLarge: Option[Boolean]
      val sampleProducer = Producer(isProducer = true, isLarge = Some(false))
      implicit val sampleProducerAsk = instances[Producer](sampleProducer)

      //sampleProducerType Large or Small
      val sampleProducerType = ProducerType.Large
      implicit val sampleProducerTypeAsk = instances[ProducerType](sampleProducerType)

      //Oragnisation Type (SoleTrader, partnership.....)
      val sampleOrganisationType = OrganisationType.soleTrader
      implicit val sampleOrganisationTypeAsk = instances[OrganisationType](sampleOrganisationType)

      //sampleOrganisationTypeSoleless limited company
      val sampleOrganisationTypeSoleless = OrganisationTypeSoleless.limitedCompany
      implicit val sampleOrganisationTypeSolelessAsk =
        instances[OrganisationTypeSoleless](sampleOrganisationTypeSoleless)

      val sampleLongPair = Some((1L, 2L))
      implicit val sampleLongPairAsk = instances[Option[(Long, Long)]](sampleLongPair)

      implicit val sampleBooleanAsk = instancesF {
        case _ => List(false)
      }

      implicit val naturalTransformation = new (Future ~> Logic) {
        override def apply[A](fa: Future[A]): Logic[A] =
          Await.result(fa, 30 seconds).pure[Logic]
      }

      val outcome: Subscription = LogicTableInterpreter
        .interpret(
          RegistrationController.journey(
            true,
            defaultFormData,
            backendCall
          ))
        .value
        .run
        .asOutcome(true)
      val subscription: Subscription = outcome
      subscription.utr shouldBe None
    }

    "construct display error" ignore {

      implicit val messages: UniformMessages[Html] = new UniformMessages[Html] {
        override def get(key: String, args: Any*): Option[Html] = Some(Html("You do not need to register"))
        override def list(key: String, args: Any*): List[Html] = List(Html("You do not need to register"))
      }

      def backendCall(s: Subscription): Future[Unit] = Future.successful(s)

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

      implicit val naturalTransformation = new (Future ~> Logic) {
        override def apply[A](fa: Future[A]): Logic[A] =
          Await.result(fa, 30 seconds).pure[Logic]
      }

      val outcome: Subscription = LogicTableInterpreter
        .interpret(
          RegistrationController.journey(
            true,
            defaultFormData,
            backendCall
          ))
        .value
        .run
        .asOutcome(true)
      val subscription: Subscription = outcome
      subscription.utr shouldBe ("1234567890")
    }

    //TODO end("do-not-register", noReg) when (noUkActivity && smallProducerWithNoCopacker)

    "construct display error 2" ignore {

      implicit val messages: UniformMessages[Html] = new UniformMessages[Html] {
        override def get(key: String, args: Any*): Option[Html] = Some(Html("You do not need to register"))
        override def list(key: String, args: Any*): List[Html] = List(Html("You do not need to register"))
      }

      def backendCall(s: Subscription): Future[Unit] = Future.successful(s)

      //Ordered Steps
      val sampleOrganisationType = OrganisationType.soleTrader
      implicit val sampleOrganisationTypeAsk = instances[OrganisationType](sampleOrganisationType)

      val sampleProducerType = ProducerType.Small
      implicit val sampleProducerTypeAsk = instances[ProducerType](sampleProducerType)

      //Asked if producer is small
      implicit val sampleBooleanAsk = instancesF {
        //    case lossKeys(_) => List(false)
        case _ => List(true)
      }

      val sampleProducer = Producer(true, Some(true))
      implicit val sampleProducerAsk = instances[Producer](sampleProducer)

      implicit val sampleListQtyAddress = SampleListQty[Address](1)
      val sampleAddress = Address.fromUkAddress(UkAddress(List(), ""))
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

      val sampleOrganisationTypeSoleless = OrganisationTypeSoleless.limitedCompany
      implicit val sampleOrganisationTypeSolelessAsk =
        instances[OrganisationTypeSoleless](sampleOrganisationTypeSoleless)

      val sampleLongPair = Some((1L, 2L))
      implicit val sampleLongPairAsk = instances[Option[(Long, Long)]](sampleLongPair)

      implicit val naturalTransformation = new (Future ~> Logic) {
        override def apply[A](fa: Future[A]): Logic[A] =
          Await.result(fa, 30 seconds).pure[Logic]
      }
      val outcome: Subscription = LogicTableInterpreter
        .interpret(
          RegistrationController.journey(
            true,
            defaultFormData,
            backendCall
          ))
        .value
        .run
        .asOutcome(true)
      val subscription: Subscription = outcome
      subscription.utr shouldBe ("1234567890")
    }

  }
}
