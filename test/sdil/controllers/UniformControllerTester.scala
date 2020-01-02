/*
 * Copyright 2020 HM Revenue & Customs
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

import play.api.libs.json._
import scala.concurrent._
import cats.implicits._
import play.api.test.FakeRequest
import play.api.mvc._
import uk.gov.hmrc.uniform.webmonad.WebMonad

case class UniformControllerTester(controller: SdilWMController) {

  case class SharedSessionPersistence(initialData: (String, JsValue)*)(implicit ec: ExecutionContext) {
    var data: Map[String, JsValue] = Map(initialData: _*)
    def dataGet(session: String): Future[Map[String, JsValue]] =
      data.pure[Future]
    def dataPut(session: String, dataIn: Map[String, JsValue]): Unit =
      data = dataIn
  }

  def testJourney(program: WebMonad[Result])(answers: (String, JsValue)*)(
    implicit ec: ExecutionContext): Future[Result] = {

    val sessionUUID = java.util.UUID.randomUUID.toString
    val persistence = SharedSessionPersistence(answers: _*)

    val request: Request[AnyContent] = FakeRequest()
      .withFormUrlEncodedBody("utr" -> "")
      .withSession { ("uuid" -> sessionUUID) }

    controller.runInner(request)(program)(
      "XXXX"
    )(persistence.dataGet, persistence.dataPut)
  }

}
