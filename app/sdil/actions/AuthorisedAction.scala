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
  extends ActionRefiner[Request, AuthorisedRequest] with ActionBuilder[AuthorisedRequest] with AuthorisedFunctions with I18nSupport with ActionHelpers {

  override protected def refine[A](request: Request[A]): Future[Either[Result, AuthorisedRequest[A]]] = {
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(request.headers, Some(request.session))

    val retrieval = allEnrolments and credentialRole and internalId

    authorised(AuthProviders(GovernmentGateway)).retrieve(retrieval) { case enrolments ~ role ~ id =>
      val error: Option[Result] = duplicateEnrolment(enrolments)(request) orElse invalidRole(role)(request)

      val internalId = id.getOrElse(throw new RuntimeException("No internal ID for user"))
      Future.successful(error.toLeft(AuthorisedRequest(getUtr(enrolments), internalId, request)))
    } recover {
      case _: NoActiveSession => Left(Redirect(config.ggLoginUrl, Map("continue" -> Seq(config.sdilHomePage), "origin" -> Seq(config.appName))))
    }
  }

  private def duplicateEnrolment(enrolments: Enrolments)(implicit request: Request[_]): Option[Result] = {
    getSdilEnrolment(enrolments) map { _ => Forbidden(errors.already_registered()) }
  }

  private def invalidRole(credentialRole: Option[CredentialRole])(implicit request: Request[_]): Option[Result] = {
    credentialRole collect {
      case Assistant => Forbidden(errors.invalid_role())
    }
  }
}

case class AuthorisedRequest[A](utr: Option[String], internalId: String, request: Request[A]) extends WrappedRequest(request)