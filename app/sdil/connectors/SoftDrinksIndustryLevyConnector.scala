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

package sdil.connectors

import play.api.libs.json.JsValue
import play.api.{Configuration, Environment}
import sdil.models._
import sdil.models.backend.Subscription
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, NotFoundException}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}

class SoftDrinksIndustryLevyConnector(http: HttpClient,
                                      environment: Environment,
                                      val runModeConfiguration: Configuration
                                     )(implicit ec: ExecutionContext) extends ServicesConfig {

  lazy val sdilUrl: String = baseUrl("soft-drinks-industry-levy")

  override protected def mode = environment.mode

  def getRosmRegistration(utr: String)(implicit hc: HeaderCarrier): Future[Option[RosmRegistration]] = {
    http.GET[Option[RosmRegistration]](s"$sdilUrl/rosm-registration/lookup/$utr")
  }

  def submit(subscription: Subscription, safeId: String)(implicit hc: HeaderCarrier): Future[Unit] = {
    http.POST[Subscription, HttpResponse](s"$sdilUrl/subscription/utr/${subscription.utr}/${safeId}", subscription) map { _ => () }
  }

  def checkPendingQueue(utr: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
//    http.GET[HttpResponse](s"$sdilUrl/check-subscription-status/$utr") recover {
    http.GET[HttpResponse](s"$sdilUrl/check-enrolment-status/$utr") recover {
      case _: NotFoundException => HttpResponse(404)
    }
  }

  def retrieveSubscription(sdilNumber: String)(implicit hc: HeaderCarrier): Future[Option[JsValue]] = {
    http.GET[Option[JsValue]](s"$sdilUrl/subscription/sdil/$sdilNumber")
  }

}
