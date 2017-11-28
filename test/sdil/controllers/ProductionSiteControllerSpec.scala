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

import org.scalatest.mockito.MockitoSugar
import org.mockito.Mockito._
import org.mockito.ArgumentMatchers.{eq => matching, _}
import play.api.i18n.Messages
import play.api.test.FakeRequest
import sdil.models.Address
import play.api.test.Helpers._
import sdil.controllers.controllerhelpers.mockCache

import scala.concurrent.Future

class ProductionSiteControllerSpec extends PlayMessagesSpec with MockitoSugar {

  "GET /production-site" should {
    import controllerhelpers.mockCache

    "return 200 Ok and the production site page if no other sites have been added" in {
      when(mockCache.fetchAndGetEntry[Seq[Address]](matching("productionSites"))(any(), any(), any()))
        .thenReturn(Future.successful(None))

      val res = testController.addSite()(FakeRequest())
      status(res) mustBe OK
      contentAsString(res) must include (Messages("sdil.productionSite.heading"))
    }

    "return 200 Ok and the add production site page if another site has been added" in {
      when(mockCache.fetchAndGetEntry[Seq[Address]](matching("productionSites"))(any(), any(), any()))
        .thenReturn(Future.successful(Some(Seq(Address("1", "", "", "", "AA11 1AA")))))

      val res = testController.addSite()(FakeRequest())
      status(res) mustBe OK
      contentAsString(res) must include (Messages("sdil.productionSite.add.heading"))
    }
  }

  "POST /production-site" should {
    "return 400 Bad Request and the production site page if no other sites have been added and the form data is invalid" in {
      when(mockCache.fetchAndGetEntry[Seq[Address]](matching("productionSites"))(any(), any(), any()))
        .thenReturn(Future.successful(None))

      val res = testController.validate()(FakeRequest())
      status(res) mustBe BAD_REQUEST
      contentAsString(res) must include (Messages("sdil.productionSite.heading"))
    }

    "return 400 Bad Request and the add production site page if another site has been added and the form data is invalid" in {
      when(mockCache.fetchAndGetEntry[Seq[Address]](matching("productionSites"))(any(), any(), any()))
        .thenReturn(Future.successful(Some(Seq(Address("1", "", "", "", "AA11 1AA")))))

      val res = testController.validate()(FakeRequest())
      status(res) mustBe BAD_REQUEST
      contentAsString(res) must include (Messages("sdil.productionSite.add.heading"))
    }

    "redirect to the add production site page if another site has been added and the form data is valid" in {
      when(mockCache.fetchAndGetEntry[Seq[Address]](matching("productionSites"))(any(), any(), any()))
        .thenReturn(Future.successful(None))

      val request = FakeRequest().withFormUrlEncodedBody(
        "hasOtherSite" -> "true",
        "otherSiteAddress.line1" -> "line 1",
        "otherSiteAddress.line2" -> "",
        "otherSiteAddress.line3" -> "",
        "otherSiteAddress.line4" -> "",
        "otherSiteAddress.postcode" -> "AA11 1AA"
      )

      val res = testController.validate()(request)
      status(res) mustBe SEE_OTHER
      redirectLocation(res) mustBe Some(routes.ProductionSiteController.addSite().url)
    }

    "redirect to the warehouse page if another site has not been added and the form data is valid" in {
      when(mockCache.fetchAndGetEntry[Seq[Address]](matching("productionSites"))(any(), any(), any()))
        .thenReturn(Future.successful(None))

      val res = testController.validate()(FakeRequest().withFormUrlEncodedBody("hasOtherSite" -> "false"))
      status(res) mustBe SEE_OTHER
      redirectLocation(res) mustBe Some(routes.WarehouseController.secondaryWarehouse().url)
    }

    "store the new address in keystore if another site has been added and the form data is valid" in {
      when(mockCache.fetchAndGetEntry[Seq[Address]](matching("productionSites"))(any(), any(), any()))
        .thenReturn(Future.successful(None))

      val request = FakeRequest().withFormUrlEncodedBody(
        "hasOtherSite" -> "true",
        "otherSiteAddress.line1" -> "line 2",
        "otherSiteAddress.line2" -> "",
        "otherSiteAddress.line3" -> "",
        "otherSiteAddress.line4" -> "",
        "otherSiteAddress.postcode" -> "AA12 2AA"
      )

      val res = testController.validate()(request)
      status(res) mustBe SEE_OTHER

      verify(mockCache, times(1))
        .cache(matching("productionSites"), matching(Seq(Address("line 2", "", "", "", "AA12 2AA"))))(any(), any(), any())
    }
  }

  "GET /production-site/remove" should {
    "remove the production site address from keystore" in {
      when(mockCache.fetchAndGetEntry[Seq[Address]](matching("productionSites"))(any(), any(), any()))
        .thenReturn(Future.successful(Some(Seq(
          Address("1", "", "", "", "AA11 1AA"),
          Address("2", "", "", "", "AA12 2AA")
        ))))

      val res = testController.remove(1)(FakeRequest())
      status(res) mustBe SEE_OTHER

      verify(mockCache, times(1))
        .cache(matching("productionSites"), matching(Seq(Address("line 1", "", "", "", "AA11 1AA"))))(any(), any(), any())
    }

    "always redirect to the production site page" in {
      when(mockCache.fetchAndGetEntry[Seq[Address]](matching("productionSites"))(any(), any(), any()))
        .thenReturn(Future.successful(Some(Seq(
          Address("1", "", "", "", "AA11 1AA"),
          Address("2", "", "", "", "AA12 2AA")
        ))))

      val res = testController.remove(1)(FakeRequest())
      status(res) mustBe SEE_OTHER
      redirectLocation(res) mustBe Some(routes.ProductionSiteController.addSite().url)
    }
  }

  lazy val testController = new ProductionSiteController(messagesApi) {
    override val cache = controllerhelpers.mockCache
  }
}
