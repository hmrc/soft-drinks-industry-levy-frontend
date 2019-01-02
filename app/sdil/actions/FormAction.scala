/*
 * Copyright 2019 HM Revenue & Customs
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
import play.api.mvc.Results._
import play.api.mvc.{ActionBuilder, Request, Result, WrappedRequest}
import sdil.config.{AppConfig, RegistrationFormDataCache}
import sdil.controllers.routes
import sdil.models.RegistrationFormData
import uk.gov.hmrc.auth.core.Enrolments
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails

import scala.concurrent.Future

class FormAction(val messagesApi: MessagesApi, cache: RegistrationFormDataCache, authorisedAction: AuthorisedAction)
                (implicit config: AppConfig)
  extends ActionBuilder[RegistrationFormRequest] with I18nSupport {

  type Body[A] = RegistrationFormRequest[A] => Future[Result]

  override def invokeBlock[A](request: Request[A], block: Body[A]): Future[Result] = {
    authorisedAction.invokeBlock[A](request, { implicit req =>
      implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(request.headers, Some(request.session))

      cache.get(req.internalId) flatMap {
        case Some(data) => block(RegistrationFormRequest(request, data, req.internalId, req.enrolments))
        case None => Future.successful(Redirect(routes.IdentifyController.show()))
      }
    })
  }

}

case class RegistrationFormRequest[T](request: Request[T],
                                      formData: RegistrationFormData,
                                      internalId: String,
                                      enrolments: Enrolments) extends WrappedRequest[T](request)
