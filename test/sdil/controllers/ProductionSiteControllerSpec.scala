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

package sdil.controllers

import java.time.LocalDate

import org.jsoup.Jsoup
import org.mockito.ArgumentMatchers.{any, eq => matching}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.i18n.Messages
import play.api.test.FakeRequest
import play.api.test.Helpers._
import sdil.models.Address

class ProductionSiteControllerSpec extends ControllerSpec with BeforeAndAfterEach {

  "GET /production-site" should {
    "return 200 Ok and the production site page if no other sites have been added" in {
      stubFormPage(productionSites = Some(Nil))

      val res = testController.addSite()(FakeRequest())
      status(res) mustBe OK
      contentAsString(res) must include(Messages("sdil.productionSite.heading"))
    }

    "return 200 Ok and the add production site page if another site has been added" in {
      stubFormPage(productionSites = Some(Seq(Address("1", "", "", "", "AA11 1AA"))))

      val res = testController.addSite()(FakeRequest())
      status(res) mustBe OK
      contentAsString(res) must include(Messages("sdil.productionSite.add.heading"))
    }

    "return a page with a link back to the start date page if the date is after the sugar tax start date" in {
      testConfig.setTaxStartDate(LocalDate.now minusDays 1)

      val res = testController.addSite()(FakeRequest())
      status(res) mustBe OK

      val html = Jsoup.parse(contentAsString(res))
      html.select("a.link-back").attr("href") mustBe routes.StartDateController.displayStartDate().url

      testConfig.resetTaxStartDate()
    }

    "return a page with a link back to the import volume page if the date is before the sugar tax start date " +
      "and the user is importing liable drinks" in {
      testConfig.setTaxStartDate(LocalDate.now plusDays 1)
      stubFormPage(imports = Some(true))

      val res = testController.addSite()(FakeRequest())
      status(res) mustBe OK

      val html = Jsoup.parse(contentAsString(res))
      html.select("a.link-back").attr("href") mustBe routes.LitreageController.show("importVolume").url
    }

    "return a page with a link back to the import page if the date is before the sugar tax start date " +
      "and the user is not importing liable drinks" in {
      testConfig.setTaxStartDate(LocalDate.now plusDays 1)
      stubFormPage(imports = Some(false))

      val res = testController.addSite()(FakeRequest())
      status(res) mustBe OK

      val html = Jsoup.parse(contentAsString(res))
      html.select("a.link-back").attr("href") mustBe routes.RadioFormController.display("import").url
    }
  }

  "POST /production-site" should {
    "return 400 Bad Request and the production site page if no other sites have been added and the form data is invalid" in {
      stubFormPage(productionSites = Some(Nil))

      val res = testController.validate()(FakeRequest())
      status(res) mustBe BAD_REQUEST
      contentAsString(res) must include(Messages("sdil.productionSite.heading"))
    }

    "return 400 Bad Request and the add production site page if another site has been added and the form data is invalid" in {
      stubFormPage(productionSites = Some(Seq(Address("1", "", "", "", "AA11 1AA"))))

      val res = testController.validate()(FakeRequest())
      status(res) mustBe BAD_REQUEST
      contentAsString(res) must include(Messages("sdil.productionSite.add.heading"))
    }

    "redirect to the add production site page if another site has been added and the form data is valid" in {
      val request = FakeRequest().withFormUrlEncodedBody(
        "hasOtherSite" -> "true",
        "otherSiteAddress.line1" -> "line 1",
        "otherSiteAddress.line2" -> "line 2",
        "otherSiteAddress.line3" -> "",
        "otherSiteAddress.line4" -> "",
        "otherSiteAddress.postcode" -> "AA11 1AA"
      )

      val res = testController.validate()(request)
      status(res) mustBe SEE_OTHER
      redirectLocation(res) mustBe Some(routes.ProductionSiteController.addSite().url)
    }

    "redirect to the warehouse page if another site has not been added and the form data is valid" in {
      val res = testController.validate()(FakeRequest().withFormUrlEncodedBody("hasOtherSite" -> "false"))
      status(res) mustBe SEE_OTHER
      redirectLocation(res) mustBe Some(routes.WarehouseController.show().url)
    }

    "store the new address in keystore if another site has been added and the form data is valid" in {
      stubFormPage(productionSites = Some(Nil))
      
      val request = FakeRequest().withFormUrlEncodedBody(
        "hasOtherSite" -> "true",
        "otherSiteAddress.line1" -> "line 2",
        "otherSiteAddress.line2" -> "line 3",
        "otherSiteAddress.line3" -> "",
        "otherSiteAddress.line4" -> "",
        "otherSiteAddress.postcode" -> "AA12 2AA"
      )

      val res = testController.validate()(request)
      status(res) mustBe SEE_OTHER

      verify(mockCache, times(1)).cache(
        matching("formData"),
        matching(defaultFormData.copy(productionSites = Some(Seq(Address("line 2", "line 3", "", "", "AA12 2AA")))))
      )(any(), any(), any())
    }
  }

  "GET /production-site/remove" should {
    "remove the production site address from keystore" in {
      stubFormPage(productionSites = Some(Seq(
        Address("1", "2", "", "", "AA11 1AA"),
        Address("2", "3", "", "", "AA12 2AA")
      )))

      val res = testController.remove(1)(FakeRequest())
      status(res) mustBe SEE_OTHER

      verify(mockCache, times(1)).cache(
        matching("formData"),
        matching(defaultFormData.copy(productionSites = Some(Seq(Address("1", "2", "", "", "AA11 1AA")))))
      )(any(), any(), any())
    }

    "always redirect to the production site page" in {
      stubFormPage(productionSites = Some(Seq(
        Address("1", "2", "", "", "AA11 1AA"),
        Address("2", "3", "", "", "AA12 2AA")
      )))

      val res = testController.remove(1)(FakeRequest())
      status(res) mustBe SEE_OTHER
      redirectLocation(res) mustBe Some(routes.ProductionSiteController.addSite().url)
    }
  }

  lazy val testController = wire[ProductionSiteController]

  override protected def beforeEach(): Unit = stubFilledInForm
}
