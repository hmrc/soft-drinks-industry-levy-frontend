/*
 * Copyright 2017 HM Revenue & Customs
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

import org.jsoup.Jsoup
import org.mockito.ArgumentMatchers.{eq => matching, _}
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.i18n.Messages
import play.api.test.FakeRequest
import play.api.test.Helpers._
import sdil.models.{Address, Packaging}

import scala.concurrent.Future

class WarehouseControllerSpec extends PlayMessagesSpec with MockitoSugar {

  "GET /secondary-warehouse" should {
    "return 200 Ok and the secondary warehouse page if no secondary warehouses have been added" in {
      when(controllerhelpers.mockCache.fetchAndGetEntry[Seq[Address]](matching("secondaryWarehouses"))(any(), any(), any()))
        .thenReturn(Future.successful(None))

      val res = testController.secondaryWarehouse()(FakeRequest())
      status(res) mustBe OK
      contentAsString(res) must include (Messages("sdil.warehouse.heading"))
    }

    "return 200 Ok and the add secondary warehouse page if other secondary warehouses have been added" in {
      when(controllerhelpers.mockCache.fetchAndGetEntry[Seq[Address]](matching("secondaryWarehouses"))(any(), any(), any()))
        .thenReturn(Future.successful(Some(Seq(Address("1", "", "", "", "AA11 1AA")))))

      val res = testController.secondaryWarehouse()(FakeRequest())
      status(res) mustBe OK
      contentAsString(res) must include (Messages("sdil.warehouse.add.heading"))
    }

    "return a page with a link back to the production sites page if the user packages liable drinks" in {
      when(controllerhelpers.mockCache.fetchAndGetEntry[Packaging](matching("packaging"))(any(), any(), any()))
        .thenReturn(Future.successful(Some(Packaging(true, true, true))))

      val res = testController.secondaryWarehouse()(FakeRequest())
      status(res) mustBe OK

      val html = Jsoup.parse(contentAsString(res))
      html.select("a.link-back").attr("href") mustBe routes.ProductionSiteController.addSite().url
    }

    "return a page with a link back to the start date page if the user does not package liable drinks" in {
      when(controllerhelpers.mockCache.fetchAndGetEntry[Packaging](matching("packaging"))(any(), any(), any()))
        .thenReturn(Future.successful(Some(Packaging(false, false, false))))

      val res = testController.secondaryWarehouse()(FakeRequest())
      status(res) mustBe OK

      val html = Jsoup.parse(contentAsString(res))
      html.select("a.link-back").attr("href") mustBe routes.StartDateController.show().url
    }
  }

  "POST /secondary-warehouse" should {
    "return 400 Bad Request and the secondary warehouse page if no secondary warehouses have been added and the form data is invalid" in {
      when(controllerhelpers.mockCache.fetchAndGetEntry[Seq[Address]](matching("secondaryWarehouses"))(any(), any(), any()))
        .thenReturn(Future.successful(None))

      val res = testController.validate()(FakeRequest())
      status(res) mustBe BAD_REQUEST
      contentAsString(res) must include (Messages("sdil.warehouse.heading"))
    }

    "return 400 Bad Request and the add secondary warehouse page if other warehouses have been added and the form data is invalid" in {
      when(controllerhelpers.mockCache.fetchAndGetEntry[Seq[Address]](matching("secondaryWarehouses"))(any(), any(), any()))
        .thenReturn(Future.successful(Some(Seq(Address("1", "", "", "", "AA11 1AA")))))

      val res = testController.validate()(FakeRequest())
      status(res) mustBe BAD_REQUEST
      contentAsString(res) must include (Messages("sdil.warehouse.add.heading"))
    }

    "redirect to the add secondary warehouse page if a warehouse has been added" in {
      when(controllerhelpers.mockCache.fetchAndGetEntry[Seq[Address]](matching("secondaryWarehouses"))(any(), any(), any()))
        .thenReturn(Future.successful(None))

      val request = FakeRequest().withFormUrlEncodedBody(
        "hasWarehouse" -> "true",
        "warehouseAddress.line1" -> "line 1",
        "warehouseAddress.line2" -> "line 2",
        "warehouseAddress.line3" -> "line 3",
        "warehouseAddress.line4" -> "line 4",
        "warehouseAddress.postcode" -> "AA11 1AA"
      )

      val res = testController.validate()(request)
      status(res) mustBe SEE_OTHER
      redirectLocation(res) mustBe Some(routes.WarehouseController.secondaryWarehouse().url)
    }

    "redirect to the contact details page if a warehouse is not added" in {
      when(controllerhelpers.mockCache.fetchAndGetEntry[Seq[Address]](matching("secondaryWarehouses"))(any(), any(), any()))
        .thenReturn(Future.successful(None))

      val res = testController.validate()(FakeRequest().withFormUrlEncodedBody("hasWarehouse" -> "false"))
      status(res) mustBe SEE_OTHER
      redirectLocation(res) mustBe Some(routes.SDILController.displayContactDetails().url)
    }

    "store the new address in keystore if a warehouse is added" in {
      when(controllerhelpers.mockCache.fetchAndGetEntry[Seq[Address]](matching("secondaryWarehouses"))(any(), any(), any()))
        .thenReturn(Future.successful(None))

      val request = FakeRequest().withFormUrlEncodedBody(
        "hasWarehouse" -> "true",
        "warehouseAddress.line1" -> "line 2",
        "warehouseAddress.line2" -> "line 3",
        "warehouseAddress.line3" -> "line 4",
        "warehouseAddress.line4" -> "line 5",
        "warehouseAddress.postcode" -> "AA11 1AA"
      )

      val res = testController.validate()(request)
      status(res) mustBe SEE_OTHER

      verify(controllerhelpers.mockCache, times(1))
        .cache(
          matching("secondaryWarehouses"),
          matching(Seq(Address("line 2", "line 3", "line 4", "line 5", "AA11 1AA")))
        )(any(), any(), any())
    }
  }

  "GET /secondary-warehouse/remove" should {
    "remove the warehouse address from keystore" in {
      val addresses = Seq(
        Address("1", "", "", "", "AA11 1AA"),
        Address("2", "", "", "", "AA12 2AA")
      )

      when(controllerhelpers.mockCache.fetchAndGetEntry[Seq[Address]](matching("secondaryWarehouses"))(any(), any(), any()))
        .thenReturn(Future.successful(Some(addresses)))

      val res = testController.remove(0)(FakeRequest())
      status(res) mustBe SEE_OTHER

      verify(controllerhelpers.mockCache, times(1))
        .cache(matching("secondaryWarehouses"), matching(Seq(Address("2", "", "", "", "AA12 2AA"))))(any(), any(), any())
    }

    "always redirect to the secondary warehouse page" in {
      when(controllerhelpers.mockCache.fetchAndGetEntry[Seq[Address]](matching("secondaryWarehouses"))(any(), any(), any()))
        .thenReturn(Future.successful(Some(Seq(Address("1", "", "", "", "AA11 1AA")))))

      val res = testController.remove(0)(FakeRequest())
      status(res) mustBe SEE_OTHER
      redirectLocation(res) mustBe Some(routes.WarehouseController.secondaryWarehouse().url)
    }
  }

  lazy val testController = new WarehouseController(messagesApi) {
    override val cache = controllerhelpers.mockCache
  }

  }
