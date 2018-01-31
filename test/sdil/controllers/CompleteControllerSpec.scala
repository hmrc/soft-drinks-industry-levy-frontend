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

import java.time.LocalDateTime

import org.scalatest.BeforeAndAfterEach
import play.api.test.FakeRequest
import play.api.test.Helpers._
import org.mockito.Mockito._
import org.mockito.ArgumentMatchers.{eq => matching, _}
import sdil.models.SubmissionData

import scala.concurrent.Future

class CompleteControllerSpec extends ControllerSpec with BeforeAndAfterEach {

  "Complete controller" should {
    "return Status: OK for displaying complete page" in {
      when(mockKeystore.fetchAndGetEntry[SubmissionData](matching("submissionData"))(any(), any(), any()))
        .thenReturn(Future.successful(Some(SubmissionData("aa@bb.cc", LocalDateTime.now, true))))

      val result = testController.displayComplete().apply(FakeRequest())

      status(result) mustBe OK
      contentAsString(result) must include(messagesApi("sdil.complete.title"))
    }
//  TODO commented but should be reinstated once returns messaging finalised
//    "return Status: OK for displaying complete page with returns information for mandatory status" in {
//      when(mockKeystore.fetchAndGetEntry[SubmissionData](matching("submissionData"))(any(), any(), any()))
//        .thenReturn(Future.successful(Some(SubmissionData("aa@bb.cc", LocalDateTime.now, false))))
//
//      val result = testController.displayComplete().apply(FakeRequest())
//
//      status(result) mustBe OK
//      contentAsString(result) must include(messagesApi("sdil.complete.what-happens.p3"))
//    }
  }

  val testController: CompleteController = wire[CompleteController]

}
