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

import com.softwaremill.macwire._
import org.mockito.ArgumentMatchers.{eq => matching, _}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.test.FakeRequest
import play.api.test.Helpers._
import sdil.models.Litreage
import sdil.models.backend._

class DeclarationControllerSpec extends ControllerSpec with BeforeAndAfterEach {

  "Declaration controller" should {
    "return Status: 200 when user is logged in and loads registerDeclaration page" in {
      val request = FakeRequest("GET", "/registerDeclaration")
      val result = testController.show.apply(request)

      status(result) mustBe OK
      contentAsString(result) must include(messagesApi("sdil.declaration.heading"))
    }

    "redirect to the registration not required page if they only produce for themselves, and the volume is fewer than 1 million litres" in {
      stubFormPage(
        packageOwnVol = Some(Litreage(1, 2)),
        volumeForCustomerBrands = None,
        packagesForOthers = Some(false),
        usesCopacker = Some(false),
        imports = Some(false),
        importVolume = None
      )

      val res = testController.show()(FakeRequest("GET", "/registerDeclaration"))

      status(res) mustBe SEE_OTHER
      redirectLocation(res).value mustBe routes.RegistrationNotRequiredController.show().url
    }

    "return Status: See Other when POST from registerDeclaration" in {
      val request = FakeRequest("POST", "/registerDeclaration")
      val result = testController.submit().apply(request)

      status(result) mustBe SEE_OTHER
      redirectLocation(result).get mustBe routes.CompleteController.show().url
    }

    "submit a valid Subscription to the backend on POST if all required form pages are complete" in {
      stubFormPage(utr = "1112223334")
      val res = testController.submit()(FakeRequest())

      status(res) mustBe SEE_OTHER

      val expected = Subscription(
        "1112223334",
        "an organisation",
        "3",
        UkAddress(List("1", "The Road"), "AA11 1AA"),
        Activity(
          Some(Litreage(1, 2)),
          Some(Litreage(9, 10)),
          Some(Litreage(3, 4)),
          Some(Litreage(1, 1)),
          isLarge = false
        ),
        LocalDate.of(2018, 4, 6),
        Seq(Site(UkAddress(List("1 Production Site St", "Production Site Town"), "AA11 1AA"), None, None, None)),
        Seq(Site(UkAddress(List("1 Warehouse Site St", "Warehouse Site Town"), "AA11 1AA"), None, None, None)),
        Contact(
          Some("A person"),
          Some("A position"),
          "1234",
          "aa@bb.cc"
        )
      )

      verify(mockSdilConnector, times(1)).submit(matching(expected), any())(any())
    }

    "redirect to the Contact Details page on POST if a required form page is missing" in {
      stubFormPage(contactDetails = None)

      val res = testController.submit()(FakeRequest())
      status(res) mustBe SEE_OTHER
      redirectLocation(res).value mustBe routes.ContactDetailsController.show().url
    }

    "translate the organisation type field to the correct enum value" in {
      lazy val expected = Subscription(
        "1112223335",
        "an organisation",
        "3",
        UkAddress(List("1", "The Road"), "AA11 1AA"),
        Activity(
          Some(Litreage(1, 2)),
          Some(Litreage(9, 10)),
          Some(Litreage(3, 4)),
          Some(Litreage(1, 1)),
          isLarge = false
        ),
        LocalDate.of(2018, 4, 6),
        Seq(Site(UkAddress(List("1 Production Site St", "Production Site Town"), "AA11 1AA"), None, None, None)),
        Seq(Site(UkAddress(List("1 Warehouse Site St", "Warehouse Site Town"), "AA11 1AA"), None, None, None)),
        Contact(
          Some("A person"),
          Some("A position"),
          "1234",
          "aa@bb.cc"
        )
      )


      Seq(
        "soleTrader" -> "1",
        "limitedLiabilityPartnership" -> "2",
        "partnership" -> "3",
        "unincorporatedBody" -> "5",
        "limitedCompany" -> "7"
      ) foreach { case (orgType, enumValue) =>
        stubFormPage(utr = "1112223335", orgType = Some(orgType))

        val res = testController.submit()(FakeRequest())
        status(res) mustBe SEE_OTHER

        verify(mockSdilConnector, times(1)).submit(matching(expected.copy(orgType = enumValue)), any())(any())
      }
    }

    "set the liability start date to the current date if it is not set" in {
      lazy val expected = Subscription(
        "2223334445",
        "an organisation",
        "3",
        UkAddress(List("1", "The Road"), "AA11 1AA"),
        Activity(
          Some(Litreage(1, 2)),
          Some(Litreage(9, 10)),
          Some(Litreage(3, 4)),
          Some(Litreage(1, 1)),
          isLarge = false
        ),
        LocalDate.now,
        Seq(Site(UkAddress(List("1 Production Site St", "Production Site Town"), "AA11 1AA"), None, None, None)),
        Seq(Site(UkAddress(List("1 Warehouse Site St", "Warehouse Site Town"), "AA11 1AA"), None, None, None)),
        Contact(
          Some("A person"),
          Some("A position"),
          "1234",
          "aa@bb.cc"
        )
      )

      stubFormPage(utr = "2223334445", startDate = None)

      val res = testController.submit()(FakeRequest())
      status(res) mustBe SEE_OTHER

      verify(mockSdilConnector, times(1)).submit(matching(expected), any())(any())
    }
  }

  lazy val testController: DeclarationController = wire[DeclarationController]

  override protected def beforeEach(): Unit = stubFilledInForm
}
