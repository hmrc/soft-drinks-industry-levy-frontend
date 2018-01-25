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

import java.time.format.DateTimeFormatter

import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import sdil.config.AppConfig
import sdil.models.SubmissionData
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import uk.gov.hmrc.play.bootstrap.http.FrontendErrorHandler
import views.html.softdrinksindustrylevy._

class CompleteController(val messagesApi: MessagesApi,
                         keystore: SessionCache,
                         errorHandler: FrontendErrorHandler)
                        (implicit config: AppConfig) extends FrontendController with I18nSupport {

  def displayComplete(): Action[AnyContent] = Action.async { implicit request =>
    keystore.fetchAndGetEntry[SubmissionData]("submissionData") map {
      case Some(SubmissionData(e, ts)) => Ok(register.complete(e, ts.format(dateFormatter), ts.format(timeFormatter)))
      case None => BadRequest(errorHandler.badRequestTemplate)
    }
  }

  lazy val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy")
  lazy val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("hh:mm a")
}
