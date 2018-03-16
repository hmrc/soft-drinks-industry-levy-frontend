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

import org.scalatest.BeforeAndAfterEach
import play.api.test.FakeRequest
import play.api.test.Helpers._
import com.softwaremill.macwire._

class SmallProducerConfirmControllerSpec extends ControllerSpec with BeforeAndAfterEach {

  "SmallProducerConfirm controller" should {
    "return Status: 200 when loading confirm page" in {
      val request = FakeRequest("GET", "/small-producer-confirm")
      val result = testController.show.apply(request)

      status(result) mustBe OK
      contentAsString(result) must include(messagesApi("sdil.small-prod-confirm.heading"))
    }
    "Confirm redirect to StartDate when user confirm exemption" in {
      val request = FakeRequest("POST", "/small-producer-confirm")
      val result = testController.submit().apply(request)

      status(result) mustBe SEE_OTHER
      redirectLocation(result).get mustBe routes.StartDateController.show().url
    }
  }
    lazy val testController: SmallProducerConfirmController = wire[SmallProducerConfirmController]

    override protected def beforeEach(): Unit = stubFilledInForm

}
