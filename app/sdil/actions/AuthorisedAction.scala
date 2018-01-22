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
import sdil.connectors.SoftDrinksIndustryLevyConnector
import uk.gov.hmrc.auth.core.AffinityGroup.Agent
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.Retrievals._
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter
import views.html.softdrinksindustrylevy.errors

import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

class AuthorisedAction(val authConnector: AuthConnector, val messagesApi: MessagesApi, sdilConnector: SoftDrinksIndustryLevyConnector)
                      (implicit config: AppConfig, ec: ExecutionContext)
  extends ActionRefiner[Request, AuthorisedRequest] with ActionBuilder[AuthorisedRequest] with AuthorisedFunctions with I18nSupport with ActionHelpers {

  override protected def refine[A](request: Request[A]): Future[Either[Result, AuthorisedRequest[A]]] = {
    implicit val req: Request[A] = request
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(request.headers, Some(request.session))

    val retrieval = allEnrolments and credentialRole and internalId and affinityGroup

    authorised(AuthProviders(GovernmentGateway)).retrieve(retrieval) { case enrolments ~ role ~ id ~ affinity =>
      val maybeUtr = getUtr(enrolments)

      val error: Option[Result] = notWhitelisted(maybeUtr)(request)
        .orElse(invalidRole(role)(request))
        .orElse(invalidAffinityGroup(affinity)(request))

      val internalId = id.getOrElse(throw new RuntimeException("No internal ID for user"))

      maybeUtr match {
        case Some(utr) =>
          sdilConnector.getRosmRegistration(utr) map {
            case Some(a) if getSdilEnrolment(enrolments).nonEmpty => Left(Forbidden(errors.already_registered(a)))
            case _ if error.nonEmpty => Left(error.get)
            case _ =>
              Right(AuthorisedRequest(maybeUtr, internalId, request))
          }
        case _ =>
          Future.successful(error.toLeft(AuthorisedRequest(maybeUtr, internalId, request)))
      }

    } recover {
      case _: NoActiveSession => Left(Redirect(sdil.controllers.routes.AuthenticationController.signIn()))
    }
  }

  private def notWhitelisted(maybeUtr: Option[String])(implicit request: Request[_]): Option[Result] = {
    maybeUtr match {
      case Some(utr) if config.whitelistEnabled && config.isWhitelisted(utr) => None
      case _ if !config.whitelistEnabled => None
      case _ => Some(Forbidden(views.html.softdrinksindustrylevy.errors.not_whitelisted()))
    }
  }

  private def invalidRole(credentialRole: Option[CredentialRole])(implicit request: Request[_]): Option[Result] = {
    credentialRole collect {
      case Assistant => Forbidden(errors.invalid_role())
    }
  }

  private def invalidAffinityGroup(affinityGroup: Option[AffinityGroup])(implicit request: Request[_]): Option[Result] = {
    affinityGroup match {
      case Some(Agent) | None => Some(Forbidden(errors.invalid_affinity()))
      case _ => None
    }
  }
}

case class AuthorisedRequest[A](utr: Option[String], internalId: String, request: Request[A]) extends WrappedRequest(request)