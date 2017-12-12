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

import play.api.mvc.Results._
import play.api.mvc.{ActionBuilder, ActionFilter, Request, Result}
import sdil.config.AppConfig
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.Retrievals._
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter

import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

class AuthorisedAction(val authConnector: AuthConnector, config: AppConfig)(implicit ec: ExecutionContext)
  extends ActionFilter[Request] with ActionBuilder[Request] with AuthorisedFunctions {

  override protected def filter[A](request: Request[A]): Future[Option[Result]] = {
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(request.headers, Some(request.session))

    authorised(AuthProviders(GovernmentGateway)).retrieve(allEnrolments and affinityGroup) { case ~(enrolments, affinity) =>
      Future.successful(invalidAccountType(affinity) orElse alreadyEnrolled(enrolments))
    } recover {
      case _: NoActiveSession => Some(Redirect(config.ggLoginUrl, Map("continue" -> Seq(config.sdilHomePage), "origin" -> Seq(config.appName))))
    }
  }

  private def invalidAccountType(affinityGroup: Option[AffinityGroup]): Option[Result] = affinityGroup match {
    case Some(AffinityGroup.Organisation) => None
    case Some(g) => Some(Unauthorized(s"Affinity group $g not permitted"))
    case None => Some(Unauthorized("No affinity group found"))
  }

  private def alreadyEnrolled(enrolments: Enrolments): Option[Result] = {
    for {
      enrolment <- enrolments.getEnrolment("HMRC-ORG-OBTDS")
      sd <- enrolment.identifiers.find(_.key.slice(2, 4) == "SD")
    } yield {
      Unauthorized("User already enrolled\nService page goes here")
    }
  }
}
