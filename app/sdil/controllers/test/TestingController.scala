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

package sdil.controllers.test

import play.api.mvc.{Action, AnyContent}
import sdil.connectors.TestConnector
import uk.gov.hmrc.play.bootstrap.controller.FrontendController


class TestingController (testConnector: TestConnector) extends FrontendController {

  def resetStore: Action[AnyContent] = Action.async {
    implicit request =>
      testConnector.resetStore map {
        x => Status(x.status)
      }
  }

  def resetDb: Action[AnyContent] = Action.async {
    implicit request =>
      testConnector.resetDb map {
        x => Status(x.status)(x.status.toString)
      }
  }

}
