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

import com.softwaremill.macwire.wire
import org.jsoup.Jsoup
import org.mockito.ArgumentMatchers.{any, eq => matching}
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.BeforeAndAfterAll
import play.api.i18n.Messages
import play.api.test.FakeRequest
import play.api.test.Helpers._
import sdil.controllers.ControllerSpec
import sdil.models.ContactDetails
import sdil.models.variations.VariationData
import uk.gov.hmrc.auth.core.retrieve.Retrievals.allEnrolments
import uk.gov.hmrc.auth.core.{Enrolment, EnrolmentIdentifier, Enrolments}

import scala.concurrent.Future

class ContactDetailsVariationControllerSpec extends ControllerSpec with BeforeAndAfterAll {

  override protected def beforeAll(): Unit = {
    val sdilEnrolment = EnrolmentIdentifier("EtmpRegistrationNumber", "XZSDIL000100107")

    when(mockAuthConnector.authorise[Enrolments](any(), matching(allEnrolments))(any(), any())).thenReturn {
      Future.successful(Enrolments(Set(Enrolment("HMRC-OBTDS-ORG", Seq(sdilEnrolment), "Active"))))
    }

    when(mockKeystore.fetchAndGetEntry[VariationData](matching("variationData"))(any(), any(), any()))
      .thenReturn(Future.successful(Some(VariationData(subscription))))
  }

  "GET /variations/contact-details" should {
    "return 200 Ok and the contact details page" in {
      val res = testController.show()(FakeRequest())
      status(res) mustBe OK

      val html = Jsoup.parse(contentAsString(res))
      html.select("h1").text mustBe Messages("sdil.contact-details.heading")
    }

    "return a page with a link back to the variations page " in {
      val response = testController.show(FakeRequest())
      status(response) mustBe OK

      val html = Jsoup.parse(contentAsString(response))
      html.select("a.link-back").attr("href") mustBe routes.VariationsController.show().url
    }
  }

  "POST /variations/contact-details" should {
    "return 400 Bad Request if the form data is invalid" in {
      val res = testController.submit()(FakeRequest().withFormUrlEncodedBody())
      status(res) mustBe BAD_REQUEST
    }

    "return 303 See Other and redirect to the summary page if the form data is valid" in {
      val request = FakeRequest().withFormUrlEncodedBody(
        "fullName" -> "foo",
        "position" -> "bar",
        "phoneNumber" -> "12310123123",
        "email" -> "foo@bar.com"
      )

      val res = testController.submit()(request)
      status(res) mustBe SEE_OTHER

      redirectLocation(res).value mustBe routes.ContactDetailsVariationController.confirm().url
    }

    "update the cached form data when the form data is valid" in {
      val data = VariationData(subscription.copy(utr = "9998887776"))

      when(mockKeystore.fetchAndGetEntry[VariationData](matching("variationData"))(any(), any(), any()))
        .thenReturn(Future.successful(Some(data)))

      val request = FakeRequest().withFormUrlEncodedBody(
        "fullName" -> "foo",
        "position" -> "bar",
        "phoneNumber" -> "12310123123",
        "email" -> "foo@bar.com"
      )

      val res = testController.submit()(request)
      status(res) mustBe SEE_OTHER

      verify(mockKeystore, times(1))
        .cache(
          matching("variationData"),
          matching(data.copy(updatedContactDetails = ContactDetails(
            "foo", "bar", "12310123123","foo@bar.com")
          ))
        )(any(), any(), any())
    }

  }

  lazy val testController: ContactDetailsVariationController = wire[ContactDetailsVariationController]
}
