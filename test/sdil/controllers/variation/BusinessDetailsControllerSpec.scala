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

import java.time.LocalDate

import sdil.controllers.ControllerSpec
import com.softwaremill.macwire._
import org.jsoup.Jsoup
import org.mockito.ArgumentMatchers.{eq => matching, _}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterAll
import play.api.test.FakeRequest
import play.api.test.Helpers._
import sdil.models.Address
import sdil.models.backend.{Contact, UkAddress}
import sdil.models.retrieved.{RetrievedActivity, RetrievedSubscription}
import sdil.models.variations.{UpdatedBusinessDetails, VariationData}
import uk.gov.hmrc.auth.core.{Enrolment, EnrolmentIdentifier, Enrolments}
import uk.gov.hmrc.auth.core.retrieve.Retrievals.allEnrolments

import scala.collection.JavaConverters._
import scala.concurrent.Future

class BusinessDetailsControllerSpec extends ControllerSpec with BeforeAndAfterAll {

  override protected def beforeAll(): Unit = {
    val sdilEnrolment = EnrolmentIdentifier("EtmpRegistrationNumber", "XZSDIL000100107")

    when(mockAuthConnector.authorise[Enrolments](any(), matching(allEnrolments))(any(), any())).thenReturn {
      Future.successful(Enrolments(Set(Enrolment("HMRC-OBTDS-ORG", Seq(sdilEnrolment), "Active"))))
    }

    when(mockKeystore.fetchAndGetEntry[VariationData](matching("variationData"))(any(), any(), any()))
      .thenReturn(Future.successful(Some(VariationData(subscription))))
  }

  "GET /variations/business-details" should {
    "return 200 Ok and the business details page" in {
      val res = testController.show()(FakeRequest())
      status(res) mustBe OK

     contentAsString(res) must include(messagesApi("sdil.declaration.your-business"))
    }

    "return a page with a link back to the variations summary page" in {
      val res = testController.show()(FakeRequest())
      status(res) mustBe OK

      val html = Jsoup.parse(contentAsString(res))
      html.select("a.link-back").attr("href") mustBe routes.VariationsController.show().url
    }

    "return a page containing a 'Trading Name' input" in {
      val res = testController.show()(FakeRequest())
      status(res) mustBe OK

      val html = Jsoup.parse(contentAsString(res))
      html.select("input").asScala.map(_.attr("name")) must contain ("tradingName")
    }

    "return a page containing 'Business address' inputs" in {
      val res = testController.show()(FakeRequest())
      status(res) mustBe OK

      val html = Jsoup.parse(contentAsString(res))
      val inputs = html.select("input").asScala.map(_.attr("name"))
      inputs must contain ("businessAddress.line1")
      inputs must contain ("businessAddress.line2")
      inputs must contain ("businessAddress.line3")
      inputs must contain ("businessAddress.line4")
      inputs must contain ("businessAddress.postcode")
    }
  }

  "POST /variations/business-details" should {
    "return 400 Bad Request if the form data is invalid" in {
      val res = testController.submit()(FakeRequest().withFormUrlEncodedBody())
      status(res) mustBe BAD_REQUEST
    }

    "return 303 See Other and redirect to the summary page if the form data is valid" in {
      val request = FakeRequest().withFormUrlEncodedBody(
        "tradingName" -> "Forbidden Right Parenthesis & Sons",
        "businessAddress.line1" -> "Rosm House",
        "businessAddress.line2" -> "Des Street",
        "businessAddress.line3" -> "Etmp Lane",
        "businessAddress.line4" -> "",
        "businessAddress.postcode" -> "AA11 1AA"
      )

      val res = testController.submit()(request)
      status(res) mustBe SEE_OTHER

      redirectLocation(res).value mustBe routes.VariationsController.show().url
    }

    "update the cached form data when the form data is valid" in {
      val data = VariationData(subscription.copy(utr = "9998887776"))

      when(mockKeystore.fetchAndGetEntry[VariationData](matching("variationData"))(any(), any(), any()))
        .thenReturn(Future.successful(Some(data)))

      val request = FakeRequest().withFormUrlEncodedBody(
        "tradingName" -> "Forbidden Right Parenthesis & Sons",
        "businessAddress.line1" -> "Rosm House",
        "businessAddress.line2" -> "Des Street",
        "businessAddress.line3" -> "Etmp Lane",
        "businessAddress.line4" -> "",
        "businessAddress.postcode" -> "AA11 1AA"
      )

      val res = testController.submit()(request)
      status(res) mustBe SEE_OTHER

      verify(mockKeystore, times(1))
        .cache(
          matching("variationData"),
          matching(data.copy(updatedBusinessDetails = UpdatedBusinessDetails(
            "Forbidden Right Parenthesis & Sons",
            Address("Rosm House", "Des Street", "Etmp Lane", "", "AA11 1AA")
          )))
        )(any(), any(), any())
    }
  }

  lazy val testController = wire[BusinessDetailsController]
  lazy val subscription: RetrievedSubscription = RetrievedSubscription(
    utr = "9876543210",
    orgName = "Forbidden Left Parenthesis & Sons",
    address = UkAddress(Seq("Rosm House", "Des Street", "Etmp Lane"), "SW1A 1AA"),
    activity = RetrievedActivity(
      smallProducer = true,
      largeProducer = true,
      contractPacker = false,
      importer = false,
      voluntaryRegistration = true
    ),
    liabilityDate = LocalDate.now,
    productionSites = Nil,
    warehouseSites = Nil,
    contact = Contact(Some("body"), Some("thing"), "-7", "aa@bb.cc")
  )
}
