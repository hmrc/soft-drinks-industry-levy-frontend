/*
 * Copyright 2018 HM Revenue & Customs
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

package sdil.controllers.variation

import com.softwaremill.macwire._
import org.jsoup.Jsoup
import org.mockito.ArgumentMatchers.{eq => matching, _}
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterAll
import play.api.i18n.Messages
import play.api.test.FakeRequest
import play.api.test.Helpers._
import sdil.controllers.ControllerSpec
import sdil.models.Address
import sdil.models.backend.{Site, UkAddress, WarehouseSite}
import sdil.models.retrieved.RetrievedActivity
import sdil.models.variations.VariationData
import uk.gov.hmrc.auth.core.retrieve.Retrievals.allEnrolments
import uk.gov.hmrc.auth.core.{Enrolment, EnrolmentIdentifier, Enrolments}

import scala.concurrent.Future

class WarehouseVariationControllerSpec extends ControllerSpec with BeforeAndAfterAll {

  "GET /secondary-warehouses" should {
    "return 200 Ok and the secondary warehouse page if no secondary warehouses have been added" in {

      val data = VariationData(subscription)
        .copy(previousPages = Seq(
          routes.VariationsController.show)
        )

      when(mockKeystore.fetchAndGetEntry[VariationData](matching("variationData"))(any(), any(), any()))
        .thenReturn(Future.successful(Some(data)))

      val res = testController.show()(FakeRequest())
      status(res) mustBe OK

      val html = Jsoup.parse(contentAsString(res))
      html.select("h1").text mustBe Messages("sdil.warehouse.heading")
      html.select("a.link-back").attr("href") mustBe routes.VariationsController.show().url

    }

    "return 200 Ok and the add secondary warehouse page if other secondary warehouses have been added" in {
      val data = VariationData(
        subscription.copy(
          warehouseSites = List(WarehouseSite(UkAddress.fromAddress(Address("1", "foo", "bar", "", "AA11 1AA")), Some("1"), None, None))
        )
      )

      when(mockKeystore.fetchAndGetEntry[VariationData](matching("variationData"))(any(), any(), any()))
      .thenReturn(Future.successful(Some(data)))

      val res = testController.show()(FakeRequest())
      status(res) mustBe OK
      contentAsString(res) must include(Messages("sdil.warehouse.add.heading"))
    }

    "return a page with a link back to the variations summary page" in {
      val data = VariationData(subscription)
        .copy(previousPages = Seq(
          routes.VariationsController.show)
        )

      when(mockKeystore.fetchAndGetEntry[VariationData](matching("variationData"))(any(), any(), any()))
        .thenReturn(Future.successful(Some(data)))

      val res = testController.show()(FakeRequest())
      status(res) mustBe OK

      val html = Jsoup.parse(contentAsString(res))
      html.select("a.link-back").attr("href") mustBe routes.VariationsController.show().url
    }

    "return a page with a link back to the imports vol page if the user is a large producer" in {
      val data = VariationData(subscription)
        .copy(previousPages = Seq(
          routes.VariationsController.show,
          routes.ImportsController.show,
          routes.ImportsVolController.show)
        )

      when(mockKeystore.fetchAndGetEntry[VariationData](matching("variationData"))(any(), any(), any()))
        .thenReturn(Future.successful(Some(data)))

      val res = testController.show()(FakeRequest())
      status(res) mustBe OK

      val html = Jsoup.parse(contentAsString(res))
      html.select("a.link-back").attr("href") mustBe routes.ImportsVolController.show().url
    }

    "redirect to the variations page if the user is voluntary" in {
      val data = VariationData(
        subscription.copy(
          activity = RetrievedActivity(
            smallProducer = true,
            largeProducer = false,
            contractPacker = false,
            importer = false,
            voluntaryRegistration = true)
        )
      )

      when(mockKeystore.fetchAndGetEntry[VariationData](matching("variationData"))(any(), any(), any()))
        .thenReturn(Future.successful(Some(data)))

      val res = testController.show()(FakeRequest())
      status(res) mustBe SEE_OTHER

      redirectLocation(res).value mustBe routes.VariationsController.show().url
    }
  }

  "POST /secondary-warehouses" should {
    "return 400 Bad Request if the no radio button is selected" in {
      val data = VariationData(
        subscription.copy(warehouseSites = Nil))

      when(mockKeystore.fetchAndGetEntry[VariationData](matching("variationData"))(any(), any(), any()))
        .thenReturn(Future.successful(Some(data)))

      val res = testController.addSingleSite()(FakeRequest())
      status(res) mustBe BAD_REQUEST
      contentAsString(res) must include(Messages("sdil.warehouse.heading"))
    }

    "return 400 Bad Request if the user adds a warehouse, but does not supply the address" in {
      val data = VariationData(
        subscription.copy(warehouseSites = Nil))

      when(mockKeystore.fetchAndGetEntry[VariationData](matching("variationData"))(any(), any(), any()))
        .thenReturn(Future.successful(Some(data)))

      val res = testController.addSingleSite()(FakeRequest().withFormUrlEncodedBody("addWarehouse" -> "true"))
      status(res) mustBe BAD_REQUEST
    }

    "redirect to the add secondary warehouse page if the user adds a warehouse" in {
      val data = VariationData(
        subscription.copy(warehouseSites = Nil))

      when(mockKeystore.fetchAndGetEntry[VariationData](matching("variationData"))(any(), any(), any()))
        .thenReturn(Future.successful(Some(data)))

      val res = testController.addSingleSite()(FakeRequest().withFormUrlEncodedBody(
        "addAddress" -> "true",
        "tradingName" -> "name trade",
        "additionalAddress.line1" -> "line 3",
        "additionalAddress.line2" -> "line 4",
        "additionalAddress.line3" -> "line 5",
        "additionalAddress.line4" -> "line 6",
        "additionalAddress.postcode" -> "AA11 1AA"
      ))

      status(res) mustBe SEE_OTHER
      redirectLocation(res).value mustBe routes.WarehouseVariationController.show().url
    }
  }

  "POST /secondary-warehouses/select-sites" should {
    "return 400 Bad Request if the user says they have a warehouse and does not fill in the address form" in {

      val data = VariationData(
        subscription.copy(warehouseSites = List(
          WarehouseSite(UkAddress.fromAddress(Address("1", "foo", "bar", "", "AA11 1AA")), Some("1"), None, None)))
      )

      when(mockKeystore.fetchAndGetEntry[VariationData](matching("variationData"))(any(), any(), any()))
        .thenReturn(Future.successful(Some(data)))


      val res = testController.addMultipleSites()(FakeRequest().withFormUrlEncodedBody("addAddress" -> "true"))
      status(res) mustBe BAD_REQUEST
      contentAsString(res) must include(Messages("sdil.warehouse.add.heading"))
    }

    "redirect to the add secondary warehouse page if a warehouse has been added" in {
      val request = FakeRequest().withFormUrlEncodedBody(
        "addAddress" -> "true",
        "tradingName" -> "name trade",
        "additionalAddress.line1" -> "line 1",
        "additionalAddress.line2" -> "line 2",
        "additionalAddress.line3" -> "line 3",
        "additionalAddress.line4" -> "line 4",
        "additionalAddress.postcode" -> "AA11 1AA"
      )

      val res = testController.addMultipleSites()(request)
      status(res) mustBe SEE_OTHER
      redirectLocation(res) mustBe Some(routes.WarehouseVariationController.show().url)
    }

  }

  lazy val testController: WarehouseVariationController = wire[WarehouseVariationController]

  override protected def beforeAll(): Unit = {
    val sdilEnrolment = EnrolmentIdentifier("EtmpRegistrationNumber", "XZSDIL000100107")

    when(mockAuthConnector.authorise[Enrolments](any(), matching(allEnrolments))(any(), any())).thenReturn {
      Future.successful(Enrolments(Set(Enrolment("HMRC-OBTDS-ORG", Seq(sdilEnrolment), "Active"))))
    }

    when(mockKeystore.fetchAndGetEntry[VariationData](matching("variationData"))(any(), any(), any()))
      .thenReturn(Future.successful(Some(VariationData(subscription))))
  }

}

