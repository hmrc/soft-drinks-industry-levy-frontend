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

package sdil.controllers.test

import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import sdil.connectors.TestConnector
import uk.gov.hmrc.play.bootstrap.controller.FrontendController

import scala.concurrent.ExecutionContext

class TestingController(testConnector: TestConnector, mcc: MessagesControllerComponents)(implicit ec: ExecutionContext)
    extends FrontendController(mcc) {

  def reset(url: String): Action[AnyContent] = Action.async { implicit request =>
    testConnector.reset(url) map { r =>
      Status(r.status)
    }
  }

  def getFile(envelopeId: String, fileId: String): Action[AnyContent] = Action.async { implicit request =>
    val contentType = fileId match {
      case "pdf" => "application/pdf"
      case "xml" => "application/xml"
      case _     => "application/octet-stream"
    }

    val actualFileId = fileId match {
      case "xml" => "xmlDocument"
      case _     => fileId
    }

    testConnector.getFile(envelopeId, actualFileId) map {
      Ok(_).withHeaders("Content-Type" -> contentType)
    }
  }

  def getVariationHtml(sdilRef: String): Action[AnyContent] = Action.async { implicit request =>
    testConnector.getVariationHtml(sdilRef) map {
      case Some(html) => Ok(html)
      case None       => NotFound
    }
  }
}
