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

package sdil.actions

import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{ActionBuilder, Request, Result, WrappedRequest}
import sdil.config.AppConfig
import sdil.models.variations.VariationData
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.HeaderCarrierConverter
import play.api.mvc.Results._
import uk.gov.hmrc.play.bootstrap.http.FrontendErrorHandler

import scala.concurrent.{ExecutionContext, Future}

class VariationAction(val messagesApi: MessagesApi,
                      cache: SessionCache,
                      registeredAction: RegisteredAction,
                      errorHandler: FrontendErrorHandler)
                     (implicit config: AppConfig, ec: ExecutionContext)
  extends ActionBuilder[VariationRequest] with I18nSupport {

  override def invokeBlock[A](request: Request[A], block: VariationRequest[A] => Future[Result]): Future[Result] = {
    registeredAction.invokeBlock[A](request, { implicit req =>
      implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(request.headers, Some(request.session))

      val maybeData = cache.fetchAndGetEntry[VariationData]("variationData")

      maybeData flatMap {
        case Some(data) => block(VariationRequest(req, data))
        case None => Future.successful(NotFound(errorHandler.notFoundTemplate))
      }
    })
  }
}

case class VariationRequest[T](wrapped: RegisteredRequest[T], data: VariationData) extends WrappedRequest(wrapped)