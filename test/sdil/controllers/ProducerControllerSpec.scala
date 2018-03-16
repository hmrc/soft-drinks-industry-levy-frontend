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

import com.softwaremill.macwire.wire
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import play.api.i18n.Messages
import play.api.test.FakeRequest
import play.api.test.Helpers.{ACCEPTED, NOT_FOUND, OK, contentAsString, status}
import uk.gov.hmrc.http.HttpResponse
import play.api.test.Helpers._

class ProducerControllerSpec extends ControllerSpec with BeforeAndAfterEach {

  "/producer" should {
    "always return 200 Ok and the producer page " in {
      when(mockSdilConnector.checkPendingQueue(any())(any())).thenReturn(HttpResponse(NOT_FOUND))
      val res = testController.show()(FakeRequest())

      status(res) mustBe OK
      contentAsString(res) must include (Messages("sdil.producer.heading"))
    }

//    "return Status: Bad Request for invalid producer form POST request and show choose option error" in {
//      val request = FakeRequest().withFormUrlEncodedBody(
//        "isProducer" -> "foo")
//      val response = testController.submit().apply(request)
//
//      status(response) mustBe BAD_REQUEST
//      contentType(response).get mustBe HTML
//      contentAsString(response) must include(messagesApi("error.radio-form.choose-option"))
//    }
  }

  lazy val testController = wire[ProducerController]

  override protected def beforeEach(): Unit = stubFilledInForm

}
