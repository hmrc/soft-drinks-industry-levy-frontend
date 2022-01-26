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

import ltbs.uniform.UniformMessages
import ltbs.uniform.interpreters.logictable.LogicTableInterpreter
import org.scalatest.{Matchers, WordSpec}
import play.twirl.api.Html
import sdil.controllers.{ControllerSpec, RegistrationController}
import sdil.models.{Address, ContactDetails, DetailsCorrect, Litreage, OrganisationDetails, Producer, RegistrationFormData, RosmRegistration}
import sdil.models.backend.{Site, Subscription}

import java.time.LocalDate
import scala.concurrent.{Await, Future, duration}
import duration._
import duration._

class RegistrationJourneySpec extends WordSpec with Matchers {

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
    "construct a subscription" in {
      implicit val messages: UniformMessages[Html] = new UniformMessages[Html] {
        override def get(key: String, args: Any*): Option[Html] = None

        override def list(key: String, args: Any*): List[Html] = List()
      }
      def backendCall(s: Subscription): Future[Unit] = Future.successful(Unit)

      val outcome: (Subscription) = LogicTableInterpreter
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
