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

import scala.concurrent.Future
import sdil.connectors.SoftDrinksIndustryLevyConnector
import java.time.LocalDate

import org.mockito.Mock
import uk.gov.hmrc.auth.core.retrieve.Retrievals._
import sdil.connectors._

class VariationsControllerSpec extends ControllerSpec {

  lazy val controller: VariationsController = wire[VariationsController]

  lazy val controllerTester = new UniformControllerTester(controller)
  implicit val hc: HeaderCarrier = HeaderCarrier()

  lazy val shortLivedCaching: ShortLivedHttpCaching = new ShortLivedHttpCaching {
    override def baseUri: String = ???
    override def domain: String = ???
    override def defaultSource: String = ???
    override def http: CoreGet with CorePut with CoreDelete = ???
  }

  "VariationsController" should {

    val aSubscription = RetrievedSubscription("0000000022", "XKSDIL000000022", "Super Lemonade Plc", UkAddress(List("63 Clifton Roundabout", "Worcester"), "WR53 7CX"), RetrievedActivity(false, true, false, false, false), LocalDate.of(2018, 4, 19), List(Site(UkAddress(List("33 Rhes Priordy", "East London"), "E73 2RP"), Some("88"), Some("Wild Lemonade Group"), Some(LocalDate.of(2018, 2, 26))), Site(UkAddress(List("117 Jerusalem Court", "St Albans"), "AL10 3UJ"), Some("87"), Some("Highly Addictive Drinks Plc"), Some(LocalDate.of(2019, 8, 19))), Site(UkAddress(List("87B North Liddle Street", "Guildford"), "GU34 7CM"), Some("94"), Some("Monster Bottle Ltd"), Some(LocalDate.of(2017, 9, 23))), Site(UkAddress(List("122 Dinsdale Crescent", "Romford"), "RM95 8FQ"), Some("27"), Some("Super Lemonade Group"), Some(LocalDate.of(2017, 4, 23))), Site(UkAddress(List("105B Godfrey Marchant Grove", "Guildford"), "GU14 8NL"), Some("96"), Some("Star Products Ltd"), Some(LocalDate.of(2017, 2, 11)))), List(), Contact(Some("Ava Adams"), Some("Chief Infrastructure Agent"), "04495 206189", "Adeline.Greene@gmail.com"), None)
    val voluntarySubscription = RetrievedSubscription("0000000022", "XKSDIL000000022", "Super Lemonade Plc", UkAddress(List("63 Clifton Roundabout", "Worcester"), "WR53 7CX"), RetrievedActivity(false, true, false, false, true), LocalDate.of(2018, 4, 19), List(Site(UkAddress(List("33 Rhes Priordy", "East London"), "E73 2RP"), Some("88"), Some("Wild Lemonade Group"), Some(LocalDate.of(2018, 2, 26))), Site(UkAddress(List("117 Jerusalem Court", "St Albans"), "AL10 3UJ"), Some("87"), Some("Highly Addictive Drinks Plc"), Some(LocalDate.of(2019, 8, 19))), Site(UkAddress(List("87B North Liddle Street", "Guildford"), "GU34 7CM"), Some("94"), Some("Monster Bottle Ltd"), Some(LocalDate.of(2017, 9, 23))), Site(UkAddress(List("122 Dinsdale Crescent", "Romford"), "RM95 8FQ"), Some("27"), Some("Super Lemonade Group"), Some(LocalDate.of(2017, 4, 23))), Site(UkAddress(List("105B Godfrey Marchant Grove", "Guildford"), "GU14 8NL"), Some("96"), Some("Star Products Ltd"), Some(LocalDate.of(2017, 2, 11)))), List(), Contact(Some("Ava Adams"), Some("Chief Infrastructure Agent"), "04495 206189", "Adeline.Greene@gmail.com"), None)

    val variableReturns = List(ReturnPeriod(2018, 1))
    val returnPeriods = List(ReturnPeriod(2018, 1))

    "execute the variations journey" in {
      val program = controller.programInner(
        aSubscription,
        aSubscription.sdilRef,
        variableReturns,
        returnPeriods
      )

      val output = controllerTester.testJourney(program)(
        "contact-details" -> Json.obj(
          "fullName" -> "Ava Adams",
          "position" -> "Chief Infrastructure Agent",
          "phoneNumber" -> "04495 206187",
          "email" -> "Adeline.Greene@gmail.com"),
        "warehouse-details_data" ->
          JsArray(List(
            Json.obj(
              "address" -> Json.obj(
                "lines" -> List("13 Bogus Crescent", "The Hyperquadrant", "Genericford", "Madeupshire"),
                "postCode" -> "ZX98 7YV"
              ),
              "tradingName" -> "Sugar Storage Ltd")
          )),
        "packaging-site-details_data" -> JsArray(List(
          Json.obj(
            "address" -> Json.obj(
              "lines" -> List("117 Jerusalem Court", "St Albansx"),
              "postCode" -> "AL10 3UJ"
            )
          ),
          Json.obj(
            "address" -> Json.obj(
              "lines" -> List("12 The Street", "Genericford"),
              "postCode" -> "AB12 3CD"
            )
          )
        )),
        "packaging-site-details" -> JsString("Done"),
        "third-party-packagers" -> JsBoolean(true),
        "production-site-details" -> JsString("Done"),
        "secondary-warehouse-details" -> JsString("Done"),
        "packaging-site" -> Json.obj("lower" -> 123, "higher" -> 234),
        "change-registered-account-details" -> JsNull,
        "secondary-warehouse-details_data" -> JsArray(List(
          Json.obj("address" -> Json.obj("lines" -> List("23 Diabetes Street", "ABC"), "postCode" -> "FG45 7CD"), "tradingName" -> "Syrupshop"))),
        "change-registered-details" -> JsArray(List("Sites", "ContactPerson", "ContactAddress").map(JsString)),
        "business-address" -> Json.obj("line1" -> "63 Clifton Roundabout", "line2" -> "Worcester", "line3" -> "Stillworcester", "line4" -> "Worcestershire", "postcode" -> "WR53 7CX"),
        "amount-produced" -> JsString("Large"),
        "contract-packing" -> Json.obj("lower" -> 2345, "higher" -> 435657),
        "warehouse-details" -> JsString("Done"),
        "production-site-details_data" -> JsArray(List(
          Json.obj("address" -> Json.obj("lines" -> List("117 Jerusalem Courtz", "St Albans"), "postCode" -> "AL10 3UJ")),
          Json.obj("address" -> Json.obj("lines" -> List("12 The Street", "Blahdy Corner"), "postCode" -> "AB12 3CD"))
        )),
        "select-change" -> JsString("Activity"),
        "imports" -> Json.obj("lower" -> 12345, "higher" -> 34668),
        "check-answers" -> JsString("Done"),
        "checkyouranswers" -> JsString("Done")
      )

      println(Await.result(output, 10 seconds))

      1 mustBe 1
    }

    "execute the variations journey Activity for empty Returns Period List" in {
      val program = controller.programInner(
        aSubscription,
        aSubscription.sdilRef,
        variableReturns,
        List()
      )

      val output = controllerTester.testJourney(program)(
        "contact-details" -> Json.obj(
          "fullName" -> "Ava Adams",
          "position" -> "Chief Infrastructure Agent",
          "phoneNumber" -> "04495 206187",
          "email" -> "Adeline.Greene@gmail.com"),
        "warehouse-details_data" ->
          JsArray(List(
            Json.obj(
              "address" -> Json.obj(
                "lines" -> List("13 Bogus Crescent", "The Hyperquadrant", "Genericford", "Madeupshire"),
                "postCode" -> "ZX98 7YV"
              ),
              "tradingName" -> "Sugar Storage Ltd")
          )),
        "packaging-site-details_data" -> JsArray(List(
          Json.obj(
            "address" -> Json.obj(
              "lines" -> List("117 Jerusalem Court", "St Albansx"),
              "postCode" -> "AL10 3UJ"
            )
          ),
          Json.obj(
            "address" -> Json.obj(
              "lines" -> List("12 The Street", "Genericford"),
              "postCode" -> "AB12 3CD"
            )
          )
        )),
        "packaging-site-details" -> JsString("Done"),
        "third-party-packagers" -> JsBoolean(true),
        "production-site-details" -> JsString("Done"),
        "secondary-warehouse-details" -> JsString("Done"),
        "packaging-site" -> Json.obj("lower" -> 123, "higher" -> 234),
        "change-registered-account-details" -> JsNull,
        "secondary-warehouse-details_data" -> JsArray(List(
          Json.obj("address" -> Json.obj("lines" -> List("23 Diabetes Street", "ABC"), "postCode" -> "FG45 7CD"), "tradingName" -> "Syrupshop"))),
        "change-registered-details" -> JsArray(List("Sites", "ContactPerson", "ContactAddress").map(JsString)),
        "business-address" -> Json.obj("line1" -> "63 Clifton Roundabout", "line2" -> "Worcester", "line3" -> "Stillworcester", "line4" -> "Worcestershire", "postcode" -> "WR53 7CX"),
        "amount-produced" -> JsString("Large"),
        "contract-packing" -> Json.obj("lower" -> 2345, "higher" -> 435657),
        "warehouse-details" -> JsString("Done"),
        "production-site-details_data" -> JsArray(List(
          Json.obj("address" -> Json.obj("lines" -> List("117 Jerusalem Courtz", "St Albans"), "postCode" -> "AL10 3UJ")),
          Json.obj("address" -> Json.obj("lines" -> List("12 The Street", "Blahdy Corner"), "postCode" -> "AB12 3CD"))
        )),
        "select-change" -> JsString("Activity"),
        "imports" -> Json.obj("lower" -> 12345, "higher" -> 34668),
        "check-answers" -> JsString("Done"),
        "checkyouranswers" -> JsString("Done")
      )

      println(Await.result(output, 10 seconds))

      1 mustBe 1
    }


    "execute the variations De-register journey for a empty returnperiods list" in {
      val program = controller.programInner(
        aSubscription,
        aSubscription.sdilRef,
        variableReturns,
        List()
      )

      val output = controllerTester.testJourney(program)(
        "contact-details" -> Json.obj(
          "fullName" -> "Ava Adams",
          "position" -> "Chief Infrastructure Agent",
          "phoneNumber" -> "04495 206187",
          "email" -> "Adeline.Greene@gmail.com"),
        "warehouse-details_data" ->
          JsArray(List(
            Json.obj(
              "address" -> Json.obj(
                "lines" -> List("13 Bogus Crescent", "The Hyperquadrant", "Genericford", "Madeupshire"),
                "postCode" -> "ZX98 7YV"
              ),
              "tradingName" -> "Sugar Storage Ltd")
          )),
        "packaging-site-details_data" -> JsArray(List(
          Json.obj(
            "address" -> Json.obj(
              "lines" -> List("117 Jerusalem Court", "St Albansx"),
              "postCode" -> "AL10 3UJ"
            )
          ),
          Json.obj(
            "address" -> Json.obj(
              "lines" -> List("12 The Street", "Genericford"),
              "postCode" -> "AB12 3CD"
            )
          )
        )),
        "packaging-site-details" -> JsString("Done"),
        "production-site-details" -> JsString("Done"),
        "secondary-warehouse-details" -> JsString("Done"),
        "packaging-site" -> Json.obj("lower" -> 123, "higher" -> 234),
        "change-registered-account-details" -> JsNull,
        "secondary-warehouse-details_data" -> JsArray(List(
          Json.obj("address" -> Json.obj("lines" -> List("23 Diabetes Street", "ABC"), "postCode" -> "FG45 7CD"), "tradingName" -> "Syrupshop"))),
        "change-registered-details" -> JsArray(List("Sites", "ContactPerson", "ContactAddress").map(JsString)),
        "business-address" -> Json.obj("line1" -> "63 Clifton Roundabout", "line2" -> "Worcester", "line3" -> "Stillworcester", "line4" -> "Worcestershire", "postcode" -> "WR53 7CX"),
        "amount-produced" -> JsString("Large"),
        "contract-packing" -> Json.obj("lower" -> 2345, "higher" -> 435657),
        "warehouse-details" -> JsString("Done"),
        "cancel-registration-reason" -> JsString("Done"),
        "production-site-details_data" -> JsArray(List(
          Json.obj("address" -> Json.obj("lines" -> List("117 Jerusalem Courtz", "St Albans"), "postCode" -> "AL10 3UJ")),
          Json.obj("address" -> Json.obj("lines" -> List("12 The Street", "Blahdy Corner"), "postCode" -> "AB12 3CD"))
        )),
        "select-change" -> JsString("Deregister"),
        "imports" -> Json.obj("lower" -> 12345, "higher" -> 34668),
        "check-answers" -> JsString("Done"),
        "checkyouranswers" -> JsString("Done")
      )

      println(Await.result(output, 10 seconds))

      1 mustBe 1
    }

    "execute the variations De-register journey for a non-Empty returnperiods list" in {
      val program = controller.programInner(
        aSubscription,
        aSubscription.sdilRef,
        variableReturns,
        returnPeriods
      )

      val output = controllerTester.testJourney(program)(
        "contact-details" -> Json.obj(
          "fullName" -> "Ava Adams",
          "position" -> "Chief Infrastructure Agent",
          "phoneNumber" -> "04495 206187",
          "email" -> "Adeline.Greene@gmail.com"),
        "warehouse-details_data" ->
          JsArray(List(
            Json.obj(
              "address" -> Json.obj(
                "lines" -> List("13 Bogus Crescent", "The Hyperquadrant", "Genericford", "Madeupshire"),
                "postCode" -> "ZX98 7YV"
              ),
              "tradingName" -> "Sugar Storage Ltd")
          )),
        "packaging-site-details_data" -> JsArray(List(
          Json.obj(
            "address" -> Json.obj(
              "lines" -> List("117 Jerusalem Court", "St Albansx"),
              "postCode" -> "AL10 3UJ"
            )
          ),
          Json.obj(
            "address" -> Json.obj(
              "lines" -> List("12 The Street", "Genericford"),
              "postCode" -> "AB12 3CD"
            )
          )
        )),
        "packaging-site-details" -> JsString("Done"),
        "production-site-details" -> JsString("Done"),
        "secondary-warehouse-details" -> JsString("Done"),
        "packaging-site" -> Json.obj("lower" -> 123, "higher" -> 234),
        "change-registered-account-details" -> JsNull,
        "secondary-warehouse-details_data" -> JsArray(List(
          Json.obj("address" -> Json.obj("lines" -> List("23 Diabetes Street", "ABC"), "postCode" -> "FG45 7CD"), "tradingName" -> "Syrupshop"))),
        "change-registered-details" -> JsArray(List("Sites", "ContactPerson", "ContactAddress").map(JsString)),
        "business-address" -> Json.obj("line1" -> "63 Clifton Roundabout", "line2" -> "Worcester", "line3" -> "Stillworcester", "line4" -> "Worcestershire", "postcode" -> "WR53 7CX"),
        "amount-produced" -> JsString("Large"),
        "contract-packing" -> Json.obj("lower" -> 2345, "higher" -> 435657),
        "warehouse-details" -> JsString("Done"),
        "cancel-registration-reason" -> JsString("Done"),
        "production-site-details_data" -> JsArray(List(
          Json.obj("address" -> Json.obj("lines" -> List("117 Jerusalem Courtz", "St Albans"), "postCode" -> "AL10 3UJ")),
          Json.obj("address" -> Json.obj("lines" -> List("12 The Street", "Blahdy Corner"), "postCode" -> "AB12 3CD"))
        )),
        "select-change" -> JsString("Deregister"),
        "imports" -> Json.obj("lower" -> 12345, "higher" -> 34668),
        "check-answers" -> JsString("Done"),
        "checkyouranswers" -> JsString("Done")
      )

      1 mustBe 1
    }

    "execute the variations journey for change type value Returns" in {

      //TODO Adam come back and fix this test so it traverses through the returns journey
      val program = controller.programInner(
        aSubscription,
        aSubscription.sdilRef,
        variableReturns,
        returnPeriods
      )

      val output = controllerTester.testJourney(program)(
        "select-change" -> JsString("Returns"),
        "select-return" -> JsString("20181"),
        "packaging(any()-site-details" -> JsString("Done"),
        "production-site-details" -> JsString("Done"),
        "secondary-warehouse-details" -> JsString("Done"),
        "packaging-site" -> Json.obj("lower" -> 123, "higher" -> 234),
        "change-registered-account-details" -> JsNull,
        "secondary-warehouse-details_data" -> JsArray(List(
          Json.obj("address" -> Json.obj("lines" -> List("23 Diabetes Street", "ABC"), "postCode" -> "FG45 7CD"), "tradingName" -> "Syrupshop"))),
        "change-registered-details" -> JsArray(List("Sites", "ContactPerson", "ContactAddress").map(JsString)),
        "business-address" -> Json.obj("line1" -> "63 Clifton Roundabout", "line2" -> "Worcester", "line3" -> "Stillworcester", "line4" -> "Worcestershire", "postcode" -> "WR53 7CX"),
        "amount-produced" -> JsString("Large"),
        "contract-packing" -> Json.obj("lower" -> 2345, "higher" -> 435657),
        "warehouse-details" -> JsString("Done"),
        "cancel-registration-reason" -> JsString("Done"),
        "production-site-details_data" -> JsArray(List(
          Json.obj("address" -> Json.obj("lines" -> List("117 Jerusalem Courtz", "St Albans"), "postCode" -> "AL10 3UJ")),
          Json.obj("address" -> Json.obj("lines" -> List("12 The Street", "Blahdy Corner"), "postCode" -> "AB12 3CD"))
        )),
        "imports" -> Json.obj("lower" -> 12345, "higher" -> 34668),
        "check-answers" -> JsString("Done"),
        "checkyouranswers" -> JsString("Done"),
        "own-brands-packaged-at-own-sites" -> Json.obj("lower" -> 123234, "higher" -> 2340000),
        "packaged-as-a-contract-packer" -> Json.obj("lower" -> 1234579, "higher" -> 2345679),
        "_editSmallProducers" -> Json.toJson(true),
        "exemptions-for-small-producers" -> Json.toJson(false),
        "small-producer-details" -> JsString("Done"),
        "brought-into-uk" -> Json.obj("lower" -> 1234562, "higher" -> 2345672),
        "brought-into-uk-from-small-producers" -> Json.obj("lower" -> 1234, "higher" -> 2345),
        "claim-credits-for-exports" -> Json.obj("lower" -> 6789, "higher" -> 2345),
        "claim-credits-for-lost-damaged" -> Json.obj("lower" -> 123, "higher" -> 234)
      )

      returnsDataCheck(returnPeriods)
      getOneReturn(sdilReturn)

      status(output) mustBe SEE_OTHER
    }


    "execute the Sites journey for " in {
      val program = controller.programInner(
        aSubscription,
        aSubscription.sdilRef,
        variableReturns,
        returnPeriods
      )

      val output = controllerTester.testJourney(program)(
        "contact-details" -> Json.obj(
          "fullName" -> "Ava Adams",
          "position" -> "Chief Infrastructure Agent",
          "phoneNumber" -> "04495 206187",
          "email" -> "Adeline.Greene@gmail.com"),
        "warehouse-details_data" ->
          JsArray(List(
            Json.obj(
              "address" -> Json.obj(
                "lines" -> List("13 Bogus Crescent", "The Hyperquadrant", "Genericford", "Madeupshire"),
                "postCode" -> "ZX98 7YV"
              ),
              "tradingName" -> "Sugar Storage Ltd")
          )),
        "packaging-site-details_data" -> JsArray(List(
          Json.obj(
            "address" -> Json.obj(
              "lines" -> List("117 Jerusalem Court", "St Albansx"),
              "postCode" -> "AL10 3UJ"
            )
          ),
          Json.obj(
            "address" -> Json.obj(
              "lines" -> List("12 The Street", "Genericford"),
              "postCode" -> "AB12 3CD"
            )
          )
        )),
        "packaging-site-details" -> JsString("Done"),
        "production-site-details" -> JsString("Done"),
        "secondary-warehouse-details" -> JsString("Done"),
        "packaging-site" -> Json.obj("lower" -> 123, "higher" -> 234),
        "change-registered-account-details" -> JsNull,
        "secondary-warehouse-details_data" -> JsArray(List(
          Json.obj("address" -> Json.obj("lines" -> List("23 Diabetes Street", "ABC"), "postCode" -> "FG45 7CD"), "tradingName" -> "Syrupshop"))),
        "change-registered-details" -> JsArray(List("Sites", "ContactPerson", "ContactAddress").map(JsString)),
        "business-address" -> Json.obj("line1" -> "63 Clifton Roundabout", "line2" -> "Worcester", "line3" -> "Stillworcester", "line4" -> "Worcestershire", "postcode" -> "WR53 7CX"),
        "amount-produced" -> JsString("Large"),
        "contract-packing" -> Json.obj("lower" -> 2345, "higher" -> 435657),
        "warehouse-details" -> JsString("Done"),
        "cancel-registration-reason" -> JsString("Done"),
        "production-site-details_data" -> JsArray(List(
          Json.obj("address" -> Json.obj("lines" -> List("117 Jerusalem Courtz", "St Albans"), "postCode" -> "AL10 3UJ")),
          Json.obj("address" -> Json.obj("lines" -> List("12 The Street", "Blahdy Corner"), "postCode" -> "AB12 3CD"))
        )),
        "select-change" -> JsString("Sites"),
        "imports" -> Json.obj("lower" -> 12345, "higher" -> 34668),
        "check-answers" -> JsString("Done"),
        "checkyouranswers" -> JsString("Done")
      )

      println(Await.result(output, 10 seconds))

      1 mustBe 1
    }


    "execute changeBusinessAddress journey" in {
      val program = controller.changeBusinessAddressJourney(
        aSubscription,
        aSubscription.sdilRef
      )

      val output = controllerTester.testJourney(program)(
        "contact-details" -> Json.obj(
          "fullName" -> "Ava Adams",
          "position" -> "Chief Infrastructure Agent",
          "phoneNumber" -> "04495 206187",
          "email" -> "Adeline.Greene@gmail.com"),
        "warehouse-details_data" -> JsArray(List(Json.obj(
          "address" -> Json.obj(
            "lines" -> List("13 Bogus Crescent", "The Hyperquadrant", "Genericford", "Madeupshire"),
            "postCode" -> "ZX98 7YV"
          ),
          "tradingName" -> "Sugar Storage Ltd")
        )),
        "packaging-site-details_data" -> JsArray(List(
          Json.obj(
            "address" -> Json.obj(
              "lines" -> List("117 Jerusalem Court", "St Albansx"),
              "postCode" -> "AL10 3UJ")),
          Json.obj(
            "address" -> Json.obj(
              "lines" -> List("12 The Street", "Genericford"),
              "postCode" -> "AB12 3CD")
          ))),
        "packaging-site-details" -> JsString("Done"),
        "change-registered-account-details" -> JsNull,
        "change-registered-details" -> JsArray(List(
          JsString("Sites"), JsString("ContactPerson"), JsString("ContactAddress"))),
        "business-address" -> Json.obj(
          "line1" -> "63 Clifton Roundabout",
          "line2" -> "Worcester",
          "line3" -> "Stillworcester",
          "line4" -> "Worcestershire",
          "postcode" -> "WR53 7CX"
        ),
        "warehouse-details" -> JsString("Done"),
        "check-answers" -> JsString("Done"),
        "checkyouranswers" -> JsString("Done")
      )

      println(Await.result(output, 10 seconds))

      1 mustBe 1
    }


//    "execute the variations journey from program" in {
//      val program = controller.program(aSubscription, aSubscription.sdilRef)
//
//      returnsDataCheck(variableReturns)
//      returnsPendingCheck(variableReturns)
//      val mockInner = mock[VariationsController]
//      when(mockInner.programInner(aSubscription,
//        aSubscription.sdilRef,
//        variableReturns,
//        returnPeriods)) thenReturn program
//
//      val output = controllerTester.testJourney(program)("null" -> JsNull)
//
//      status(output) mustBe SEE_OTHER
//    }

    "execute adjustment journey" in {
      val sdilEnrolment = EnrolmentIdentifier("EtmpRegistrationNumber", "XZSDIL000100107")
      when(mockAuthConnector.authorise[Enrolments](any(), matching(allEnrolments))(any(), any())).thenReturn {
        Future.successful(Enrolments(Set(Enrolment("HMRC-OBTDS-ORG", Seq(sdilEnrolment), "Active"))))
      }
      when(mockSdilConnector.retrieveSubscription(matching("XZSDIL000100107"), anyString())(any())).thenReturn {
        Future.successful(Some(aSubscription))
      }

      val result = controller.adjustment(2018, 1, "idType").apply(FakeRequest().withFormUrlEncodedBody("sdilEnrolment" -> "someValue"))
      status(result) mustEqual SEE_OTHER

    }

    "execute index journey" in {
      val sdilEnrolment = EnrolmentIdentifier("EtmpRegistrationNumber", "XZSDIL000100107")
      when(mockAuthConnector.authorise[Enrolments](any(), matching(allEnrolments))(any(), any())).thenReturn {
        Future.successful(Enrolments(Set(Enrolment("HMRC-OBTDS-ORG", Seq(sdilEnrolment), "Active"))))
      }
      when(mockSdilConnector.retrieveSubscription(matching("XZSDIL000100107"), anyString())(any())).thenReturn {
        Future.successful(Some(aSubscription))
      }

      val result = controller.index("idvalue").apply(FakeRequest().withFormUrlEncodedBody("sdilEnrolment" -> "someValue"))
      status(result) mustEqual SEE_OTHER

    }

    "execute changeBusinessAddress journey from index" in {
      val sdilEnrolment = EnrolmentIdentifier("EtmpRegistrationNumber", "XZSDIL000100107")
      when(mockAuthConnector.authorise[Enrolments](any(), matching(allEnrolments))(any(), any())).thenReturn {
        Future.successful(Enrolments(Set(Enrolment("HMRC-OBTDS-ORG", Seq(sdilEnrolment), "Active"))))
      }
      when(mockSdilConnector.retrieveSubscription(matching("XZSDIL000100107"), anyString())(any())).thenReturn {
        Future.successful(Some(aSubscription))
      }

      val result = controller.changeBusinessAddress("idvalue").apply(FakeRequest().withFormUrlEncodedBody("sdilEnrolment" -> "someValue"))
      status(result) mustEqual SEE_OTHER

    }

    "execute changeActorStatus journey from index" in {
      val sdilEnrolment = EnrolmentIdentifier("EtmpRegistrationNumber", "XZSDIL000100107")
      when(mockAuthConnector.authorise[Enrolments](any(), matching(allEnrolments))(any(), any())).thenReturn {
        Future.successful(Enrolments(Set(Enrolment("HMRC-OBTDS-ORG", Seq(sdilEnrolment), "Active"))))
      }
      when(mockSdilConnector.retrieveSubscription(matching("XZSDIL000100107"), anyString())(any())).thenReturn {
        Future.successful(Some(aSubscription))
      }
      when(mockSdilConnector.returns_pending(matching("0000000022"))(any())).thenReturn(Future.successful((returnPeriods)))

      val result = controller.changeActorStatus("idvalue").apply(FakeRequest().withFormUrlEncodedBody("sdilEnrolment" -> "someValue"))
      status(result) mustEqual SEE_OTHER

    }
  }
}

// URI: http://localhost:8700/soft-drinks-industry-levy/variations/checkyouranswers
// DATA OUT:Map(contact-details -> {"fullName":"Ava Adams","position":"Chief Infrastructure Agent","phoneNumber":"04495 206187","email":"Adeline.Greene@gmail.com"}, warehouse-details_data -> [{"address":{"lines":["13 Bogus Crescent","The Hyperquadrant","Genericford","Madeupshire"],"postCode":"ZX98 7YV"},"tradingName":"Sugar Storage Ltd"}], packaging-site-details_data -> [{"address":{"lines":["117 Jerusalem Court","St Albansx"],"postCode":"AL10 3UJ"}},{"address":{"lines":["12 The Street","Genericford"],"postCode":"AB12 3CD"}}], packaging-site-details -> "Done", change-registered-account-details -> null, change-registered-details -> ["Sites","ContactPerson","ContactAddress"], business-address -> {"line1":"63 Clifton Roundabout","line2":"Worcester","line3":"Stillworcester","line4":"Worcestershire","postcode":"WR53 7CX"}, warehouse-details -> "Done")
