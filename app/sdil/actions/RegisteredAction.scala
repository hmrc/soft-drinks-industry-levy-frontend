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
import play.api.mvc._
import sdil.config.AppConfig
import sdil.connectors.SoftDrinksIndustryLevyConnector
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.Retrievals.allEnrolments
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails

import scala.concurrent.Future

class RegisteredAction(val authConnector: AuthConnector, sdilConnector: SoftDrinksIndustryLevyConnector)
                      (implicit config: AppConfig)
  extends ActionRefiner[Request, RegisteredRequest] with ActionBuilder[RegisteredRequest] with AuthorisedFunctions with ActionHelpers {

  override protected def refine[A](request: Request[A]): Future[Either[Result, RegisteredRequest[A]]] = {
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(request.headers, Some(request.session))

    authorised(AuthProviders(GovernmentGateway)).retrieve(allEnrolments) { enrolments =>
      Future.successful {
        getSdilEnrolment(enrolments) match {
          case Some(e) => Right(RegisteredRequest(e, request))
          case None => Left(Redirect(sdil.controllers.routes.IdentifyController.start()))
        }
      }
    } recover {
      case _: NoActiveSession => Left(Redirect(sdil.controllers.routes.AuthenticationController.signIn()))
    }
  }
}

case class RegisteredRequest[A](sdilEnrolment: EnrolmentIdentifier, request: Request[A]) extends WrappedRequest(request)
