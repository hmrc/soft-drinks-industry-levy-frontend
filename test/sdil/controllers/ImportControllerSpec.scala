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

import org.mockito.ArgumentMatchers.{eq => matching, _}
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.test.FakeRequest
import play.api.test.Helpers._
import sdil.models.Identification
import uk.gov.hmrc.http.cache.client.{CacheMap, SessionCache}

import scala.concurrent.Future

class ImportControllerSpec extends PlayMessagesSpec with MockitoSugar {

  "GET /import" should {
    "always return 200 Ok and the imports page" in {
      val res = testController.display(FakeRequest())
      status(res) mustBe OK
      contentAsString(res) must include("Will you bring liable drinks into the UK from anywhere outside of the UK?")
    }
  }

  "POST /import" should {
    "return 400 - Bad Request and the Import page when the form data is invalid" in {
      val request = FakeRequest()
      val res = testController.submit(request)

      status(res) mustBe BAD_REQUEST
      contentAsString(res) must include("You have not chosen an option")
    }
    "redirect to the import-volume page if the form data is true" in {
      {
        val request = FakeRequest().withFormUrlEncodedBody("isImport" -> "true")
        val res = testController.submit(request)

        status(res) mustBe SEE_OTHER
        redirectLocation(res).value mustBe routes.LitreageController.show("importVolume").url
      }
    }
    "redirect to the start date page if the form data is false" in {
      {
        val request = FakeRequest().withFormUrlEncodedBody("isImport" -> "false")
        val res = testController.submit(request)

        status(res) mustBe SEE_OTHER
        redirectLocation(res).value mustBe routes.StartDateController.show().url
      }
    }
  }
  lazy val testController = new ImportController(messagesApi) {
    override val cache: SessionCache = controllerhelpers.mockCache
  }
}
