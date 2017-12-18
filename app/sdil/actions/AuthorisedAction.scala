/*
 * Copyright 2017 HM Revenue & Customs
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
import play.api.mvc._
import sdil.config.AppConfig
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.Retrievals._
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter
import views.html.softdrinksindustrylevy.errors

import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

class AuthorisedAction(val authConnector: AuthConnector, val messagesApi: MessagesApi)
                      (implicit config: AppConfig, ec: ExecutionContext)
  extends ActionRefiner[Request, EnrolmentRequest] with ActionBuilder[EnrolmentRequest] with AuthorisedFunctions with I18nSupport {

  override protected def refine[A](request: Request[A]): Future[Either[Result, EnrolmentRequest[A]]] = {
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(request.headers, Some(request.session))

    authorised(AuthProviders(GovernmentGateway)).retrieve(allEnrolments and credentialRole) { case ~(enrolments, role) =>
      val error: Option[Result] = duplicateEnrolment(enrolments)(request) orElse invalidRole(role)(request)

      Future.successful(error.toLeft(EnrolmentRequest(enrolments, request)))
    } recover {
      case _: NoActiveSession => Left(Redirect(config.ggLoginUrl, Map("continue" -> Seq(config.sdilHomePage), "origin" -> Seq(config.appName))))
    }
  }

  private def duplicateEnrolment(enrolments: Enrolments)(implicit request: Request[_]): Option[Result] = {
    for {
      enrolment <- enrolments.getEnrolment("HMRC-ORG-OBTDS")
      sdil <- enrolment.identifiers.find(id => id.key == "EtmpRegistrationNumber" && id.value.slice(2, 4) == "SD")
    } yield {
      Forbidden(errors.already_registered())
    }
  }

  private def invalidRole(credentialRole: Option[CredentialRole])(implicit request: Request[_]): Option[Result] = {
    credentialRole collect {
      case Assistant => Forbidden(errors.invalid_role())
    }
  }
}

case class EnrolmentRequest[A](enrolments: Enrolments, request: Request[A]) extends WrappedRequest(request)