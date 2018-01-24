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

import play.api.mvc.Results._
import play.api.mvc.{ActionBuilder, Request, Result, WrappedRequest}
import sdil.config.FormDataCache
import sdil.controllers.routes
import sdil.models.RegistrationFormData
import uk.gov.hmrc.auth.core.{Enrolment, Enrolments}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter

import scala.concurrent.{ExecutionContext, Future}

class FormAction(cache: FormDataCache, authorisedAction: AuthorisedAction)(implicit ec: ExecutionContext)
  extends ActionBuilder[RegistrationFormRequest] {

  type Body[A] = RegistrationFormRequest[A] => Future[Result]

  override def invokeBlock[A](request: Request[A], block: Body[A]): Future[Result] = authorisedAction.invokeBlock[A](request, { req =>
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(request.headers, Some(request.session))

    cache.get(req.internalId) flatMap {
      case Some(data) => block(RegistrationFormRequest(request, data, req.internalId, req.enrolments))
      case None => Future.successful(Redirect(routes.IdentifyController.start()))
    }
  })
}

case class RegistrationFormRequest[T](request: Request[T],
                                      formData: RegistrationFormData,
                                      internalId: String,
                                      enrolments: Enrolments) extends WrappedRequest[T](request)
