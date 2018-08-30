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

import play.api.{Configuration, Environment}
import sdil.models._
import sdil.models.backend.{Site, Subscription}
import sdil.models.retrieved.RetrievedSubscription
import sdil.models.variations.VariationsSubmission
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, NotFoundException}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails
import play.api.libs.json._
import uk.gov.hmrc.http.cache.client.ShortLivedHttpCaching

import scala.concurrent.Future

class SoftDrinksIndustryLevyConnector(http: HttpClient,
                                      environment: Environment,
                                      val runModeConfiguration: Configuration,
                                      val shortLiveCache: ShortLivedHttpCaching) extends ServicesConfig {

  lazy val sdilUrl: String = baseUrl("soft-drinks-industry-levy")

  override protected def mode = environment.mode

  def getRosmRegistration(utr: String)(implicit hc: HeaderCarrier): Future[Option[RosmRegistration]] = {
    http.GET[Option[RosmRegistration]](s"$sdilUrl/rosm-registration/lookup/$utr")
  }

  def submit(subscription: Subscription, safeId: String)(implicit hc: HeaderCarrier): Future[Unit] = {
    http.POST[Subscription, HttpResponse](s"$sdilUrl/subscription/utr/${subscription.utr}/$safeId", subscription) map { _ => () }
  }

  def checkPendingQueue(utr: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    http.GET[HttpResponse](s"$sdilUrl/check-enrolment-status/$utr") recover {
      case _: NotFoundException => HttpResponse(404)
    }
  }

  def retrieveSubscription(sdilNumber: String)(implicit hc: HeaderCarrier): Future[Option[RetrievedSubscription]] = {
    shortLiveCache.fetchAndGetEntry[RetrievedSubscription]("sdil-foo", s"$sdilNumber") flatMap {
      case Some(s) => Future.successful(Some(s))
      case _ =>
        http.GET[Option[RetrievedSubscription]](s"$sdilUrl/subscription/sdil/$sdilNumber").flatMap {
          case Some(a) =>
            shortLiveCache.cache("sdil-foo", s"$sdilNumber", a).map {
              _ => Some(a)
            }
          case _ => Future.successful(None)
        }
    }
  }

  def submitVariation(variation: VariationsSubmission, sdilNumber: String)(implicit hc: HeaderCarrier): Future[Unit] = {
    http.POST[VariationsSubmission, HttpResponse](s"$sdilUrl/submit-variations/sdil/$sdilNumber", variation) map { _ => () }
  }

  object returns { 
    implicit val returnPeriodJson = Json.format[ReturnPeriod]
    import ltbs.play.scaffold.SdilComponents.longTupleFormatter
    implicit val smallProducerJson = Json.format[SmallProducer]    
    implicit val returnJson = Json.format[SdilReturn]

    def pending(
      utr: String
    )(implicit hc: HeaderCarrier): Future[List[ReturnPeriod]] = {
      http.GET[List[ReturnPeriod]](s"$sdilUrl/returns/$utr/pending")
    }

    def update(
      utr: String,
      period: ReturnPeriod,
      sdilReturn: SdilReturn
    )(implicit hc: HeaderCarrier): Future[Unit] = {
      val uri = s"$sdilUrl/returns/$utr/year/${period.year}/quarter/${period.quarter}"
      http.POST[SdilReturn, HttpResponse](uri, sdilReturn) map { _ => () }
    }

    def get(
      utr: String,
      period: ReturnPeriod
    )(implicit hc: HeaderCarrier): Future[Option[SdilReturn]] = {
      val uri = s"$sdilUrl/returns/$utr/year/${period.year}/quarter/${period.quarter}"
      http.GET[Option[SdilReturn]](uri)
    }

    def variation(variation: ReturnsVariation, sdilRef: String)(implicit hc: HeaderCarrier): Future[Unit] = {
      http.POST[ReturnsVariation, HttpResponse](s"$sdilUrl/returns/variation/sdil/$sdilRef", variation) map { _ => () }
    }

  }

  def balance(
    sdil: String
  )(implicit hc: HeaderCarrier): Future[BigDecimal] = {
    http.GET[BigDecimal](s"$sdilUrl/balance/$sdil")
  }

  def balanceHistory(
    sdil: String
  )(implicit hc: HeaderCarrier): Future[List[FinancialLineItem]] = {
    import FinancialLineItem.formatter
    http.GET[List[FinancialLineItem]](s"$sdilUrl/balance/$sdil/history")
  }

}
