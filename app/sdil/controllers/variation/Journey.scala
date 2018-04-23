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

import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import sdil.actions.VariationRequest
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.bootstrap.controller.FrontendController

import scala.concurrent.Future

trait Journey extends FrontendController with I18nSupport {

  val cache: SessionCache
  val messagesApi: MessagesApi

  def backLink(controllerPage: Call)(implicit request: VariationRequest[_]): Future[Call] = {
    request.data.previousPages match {
      case pages if pages.contains(controllerPage) =>
        val updatedPages = pages.slice(0, pages.indexOf(controllerPage) +1)
        cache.cache(
          "variationData",
          request.data.copy(previousPages = updatedPages)
        ) map { _ =>
          updatedPages.init.last
        }
      case pages =>
        cache.cache(
          "variationData",
          request.data.copy(previousPages = pages :+ controllerPage)
        ) map { _ =>
          pages.last
        }
    }
  }
}
