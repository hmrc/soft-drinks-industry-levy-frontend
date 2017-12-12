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

import org.scalatest.BeforeAndAfterEach
import play.api.test.FakeRequest
import play.api.test.Helpers._

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
  }

  lazy val testController: DeclarationController = wire[DeclarationController]

  override protected def beforeEach(): Unit = stubFilledInForm
}
