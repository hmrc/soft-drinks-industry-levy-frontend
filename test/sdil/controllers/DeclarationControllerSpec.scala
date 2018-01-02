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

import org.mockito.ArgumentMatchers.{eq => matching, _}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.test.FakeRequest
import play.api.test.Helpers._
import sdil.models.Litreage
import sdil.models.backend._

class DeclarationControllerSpec extends ControllerSpec with BeforeAndAfterEach {

  "Declaration controller" should {
    "return Status: 200 when user is logged in and loads declaration page" in {
      val request = FakeRequest("GET", "/declaration")
      val result = testController.displayDeclaration.apply(request)

      status(result) mustBe OK
      contentAsString(result) must include(messagesApi("sdil.declaration.heading"))
    }

    "return Status: See Other when POST from declaration" in {
      val request = FakeRequest("POST", "/declaration")
      val result = testController.submitDeclaration().apply(request)

      status(result) mustBe SEE_OTHER
      redirectLocation(result).get mustBe routes.SDILController.displayComplete().url
    }

    "submit a valid Subscription to the backend on POST if all required form pages are complete" in {
      stubFormPage(utr = "1112223334")
      val res = testController.submitDeclaration()(FakeRequest())

      status(res) mustBe SEE_OTHER

      val expected = Subscription(
        "1112223334",
        "an organisation",
        "3",
        UkAddress(Seq("1", "The Road"), "AA11 1AA"),
        Activity(
          Some(Litreage(1, 2)),
          Some(Litreage(9, 10)),
          Some(Litreage(3, 4)),
          Some(Litreage(5, 6)),
          Some(Litreage(7, 8))
        ),
        LocalDate.of(2018, 4, 6),
        Seq(Site(UkAddress(Seq("1 Production Site St", "Production Site Town"), "AA11 1AA"))),
        Seq(Site(UkAddress(Seq("1 Warehouse Site St", "Warehouse Site Town"), "AA11 1AA"))),
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

      val res = testController.submitDeclaration()(FakeRequest())
      status(res) mustBe SEE_OTHER
      redirectLocation(res).value mustBe routes.ContactDetailsController.displayContactDetails().url
    }

    "translate the organisation type field to the correct enum value" in {
      lazy val expected = Subscription(
        "1112223335",
        "an organisation",
        "3",
        UkAddress(Seq("1", "The Road"), "AA11 1AA"),
        Activity(
          Some(Litreage(1, 2)),
          Some(Litreage(9, 10)),
          Some(Litreage(3, 4)),
          Some(Litreage(5, 6)),
          Some(Litreage(7, 8))
        ),
        LocalDate.of(2018, 4, 6),
        Seq(Site(UkAddress(Seq("1 Production Site St", "Production Site Town"), "AA11 1AA"))),
        Seq(Site(UkAddress(Seq("1 Warehouse Site St", "Warehouse Site Town"), "AA11 1AA"))),
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

        val res = testController.submitDeclaration()(FakeRequest())
        status(res) mustBe SEE_OTHER

        verify(mockSdilConnector, times(1)).submit(matching(expected.copy(orgType = enumValue)), any())(any())
      }
    }
  }

  lazy val testController: DeclarationController = wire[DeclarationController]

  override protected def beforeEach(): Unit = stubFilledInForm
}
