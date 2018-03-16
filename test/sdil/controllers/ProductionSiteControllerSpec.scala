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
import sdil.models.DetailsCorrect.DifferentAddress
import sdil.models.{Address, OrganisationDetails, RosmRegistration, Litreage}
import com.softwaremill.macwire._

import scala.collection.JavaConverters._

class ProductionSiteControllerSpec extends ControllerSpec with BeforeAndAfterEach {

  "GET /production-site" should {
    "return 200 Ok and the production site page if no other sites have been added" in {
      stubFormPage(productionSites = None,
        imports = Some(false),
        smallProducerConfirmFlag = Some(true))

      val res = testController.show()(FakeRequest())
      status(res) mustBe OK
      contentAsString(res) must include(Messages("sdil.productionSite.heading"))
    }

    "return a page including the company's registered address" in {
      val bprAddress = Address("1 The Place", "Somewhere", "", "", "AA11 1AA")
      val orgDetails = OrganisationDetails("a company")

      stubFormPage(rosmData = RosmRegistration("a-safe-id", Some(orgDetails), None, bprAddress), productionSites = Some(Nil))

      val res = testController.show()(FakeRequest())
      status(res) mustBe OK

      val html = Jsoup.parse(contentAsString(res))
      html.select("#bprAddress").next("label").text mustBe bprAddress.nonEmptyLines.mkString(", ")
    }

    "return a page including the company's primary place of business if it differs from their registered address" in {
      val ppob = Address("2 The Place", "Somewhere else", "", "", "AA11 1AA")
      stubFormPage(verify = Some(DifferentAddress(ppob)), productionSites = Some(Nil))

      val res = testController.show()(FakeRequest())
      status(res) mustBe OK

      val html = Jsoup.parse(contentAsString(res))
      html.select("#ppobAddress").next("label").text mustBe ppob.nonEmptyLines.mkString(", ")
    }

    "return a page including all production sites added so far" in {
      val sites = Seq(
        Address("3 The Place", "Another place", "", "", "AA11 1AA"),
        Address("4 The Place", "Another place", "", "", "AA12 2AA"),
        Address("5 The Place", "Another place", "", "", "AA13 3AA")
      )

      stubFormPage(productionSites = Some(sites))

      val res = testController.show()(FakeRequest())
      status(res) mustBe OK

      val html = Jsoup.parse(contentAsString(res))
      val checkboxLabels = html.select(""".multiple-choice input[type="checkbox"]""")
        .asScala.filter(_.id.startsWith("additionalSites")).map(_.nextElementSibling().text)

      checkboxLabels must contain theSameElementsAs sites.map(_.nonEmptyLines.mkString(", "))
    }

    "return a page with a link back to the start date page if the date is after the sugar tax start date" in {
      testConfig.setTaxStartDate(yesterday)

      val res = testController.show()(FakeRequest())
      status(res) mustBe OK

      val html = Jsoup.parse(contentAsString(res))
      html.select("a.link-back").attr("href") mustBe routes.StartDateController.show().url

      testConfig.resetTaxStartDate()
    }

    "return a page with a link back to the import volume page if the date is before the sugar tax start date " +
      "and the user is importing liable drinks" in {
      testConfig.setTaxStartDate(tomorrow)
      stubFormPage(
        packageOwn = Some(Litreage(10000000L, 10000000L)),
        imports = Some(true),
        importVolume = Some(Litreage(5, 5)))


      val res = testController.show()(FakeRequest())
      status(res) mustBe OK

      val html = Jsoup.parse(contentAsString(res))
      html.select("a.link-back").attr("href") mustBe routes.LitreageController.show("importVolume").url
    }

    "return a page with a link back to the import page if the date is before the sugar tax start date " +
      "and the user is not importing liable drinks" in {
      testConfig.setTaxStartDate(tomorrow)
      stubFormPage(packageOwn = Some(Litreage(10000000L, 10000000L)),
        imports = Some(false))

      val res = testController.show()(FakeRequest())
      status(res) mustBe OK

      val html = Jsoup.parse(contentAsString(res))
      html.select("a.link-back").attr("href") mustBe routes.RadioFormController.show("import").url
    }

    "return a page with a link back to the small producer exemption page if the date is before the sugar tax start date " +
      "and the user is not importing liable drinks" in {
      testConfig.setTaxStartDate(tomorrow)
      stubFormPage(smallProducerConfirmFlag = Some(true))

      val res = testController.show()(FakeRequest())
      status(res) mustBe OK

      val html = Jsoup.parse(contentAsString(res))
      html.select("a.link-back").attr("href") mustBe routes.SmallProducerConfirmController.show().url
    }
  }

  "POST /production-site" should {
    "return 400 Bad Request and the production sites page if the user says they need to add another address, " +
      "but do not fill in the address form" in {

      val res = testController.submit()(FakeRequest().withFormUrlEncodedBody("addAddress" -> "true"))
      status(res) mustBe BAD_REQUEST
    }

    "return 400 Bad Request when the user selects no production sites and does not add a new site" in {
      val res = testController.submit()(FakeRequest())
      status(res) mustBe BAD_REQUEST
    }

    "redirect to the production sites page if another site has been added and the form data is valid" in {
      val request = FakeRequest().withFormUrlEncodedBody(
        "addAddress" -> "true",
        "additionalAddress.line1" -> "line 1",
        "additionalAddress.line2" -> "line 2",
        "additionalAddress.line3" -> "",
        "additionalAddress.line4" -> "",
        "additionalAddress.postcode" -> "AA11 1AA"
      )

      val res = testController.submit()(request)
      status(res) mustBe SEE_OTHER
      redirectLocation(res) mustBe Some(routes.ProductionSiteController.show().url)
    }

    "redirect to the warehouse page if another site has not been added and the form data is valid" in {
      val res = testController.submit()(FakeRequest().withFormUrlEncodedBody("bprAddress" -> "true"))
      status(res) mustBe SEE_OTHER
      redirectLocation(res) mustBe Some(routes.WarehouseController.show().url)
    }

    "store the new address in keystore if another site has been added and the form data is valid" in {
      stubFormPage(productionSites = Some(Nil))

      val request = FakeRequest().withFormUrlEncodedBody(
        "addAddress" -> "true",
        "additionalAddress.line1" -> "line 2",
        "additionalAddress.line2" -> "line 3",
        "additionalAddress.line3" -> "",
        "additionalAddress.line4" -> "",
        "additionalAddress.postcode" -> "AA12 2AA"
      )

      val res = testController.submit()(request)
      status(res) mustBe SEE_OTHER

      verify(mockCache, times(1)).cache(
        matching("internal id"),
        matching(defaultFormData.copy(productionSites = Some(Seq(Address("line 2", "line 3", "", "", "AA12 2AA")))))
      )(any())
    }

    "store all selected addresses in keystore when no site is added" in {
      val bprAddress = Address("1", "Company Street", "", "", "AA11 1AA")
      val ppobAddress = Address("Somewhere", "Totally different", "", "", "AA11 1AA")
      val rosmRegistration = RosmRegistration(
        "another safe id",
        Some(OrganisationDetails("company inc. ltd llc intl")),
        None,
        bprAddress
      )

      stubFormPage(
        rosmData = rosmRegistration,
        verify = Some(DifferentAddress(ppobAddress)),
        productionSites = Some(Seq(
          Address("1", "2", "3", "4", "AA11 1AA"),
          Address("2", "3", "4", "5", "AA12 2AA")
        ))
      )

      val request = FakeRequest().withFormUrlEncodedBody(
        "bprAddress" -> bprAddress.nonEmptyLines.mkString(","),
        "ppobAddress" -> ppobAddress.nonEmptyLines.mkString(","),
        "additionalSites[1]" -> "2,3,4,5,AA12 2AA"
      )

      val res = testController.submit()(request)
      status(res) mustBe SEE_OTHER

      verify(mockCache, times(1)).cache(
        matching("internal id"),
        matching(defaultFormData.copy(
          rosmData = rosmRegistration,
          verify = Some(DifferentAddress(ppobAddress)),
          productionSites = Some(Seq(bprAddress, ppobAddress, Address("2", "3", "4", "5", "AA12 2AA"))))
        )
      )(any())
    }
  }

  lazy val testController = wire[ProductionSiteController]

  lazy val tomorrow = LocalDate.now plusDays 1
  lazy val yesterday: LocalDate = LocalDate.now minusDays 1


  override protected def beforeEach(): Unit = {
    testConfig.setTaxStartDate(yesterday)
    stubFilledInForm
  }

  override protected def afterEach(): Unit = {
    testConfig.resetTaxStartDate()
  }
}