/*
 * Copyright 2023 HM Revenue & Customs
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

import play.api.Configuration
import play.api.libs.json.JsObject
import sdil.config.SDILSessionCache
import sdil.models._
import sdil.models.backend.Subscription
import sdil.models.retrieved.RetrievedSubscription
import sdil.models.variations.{ReturnVariationData, VariationsSubmission}
import uk.gov.hmrc.http.HttpReads.Implicits.{readFromJson, readRaw, _}
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SoftDrinksIndustryLevyConnector @Inject()(
  http: HttpClient,
  val configuration: Configuration,
  val sessionCache: SDILSessionCache
)(implicit ec: ExecutionContext)
    extends ServicesConfig(configuration) {

  lazy val sdilUrl: String = baseUrl("soft-drinks-industry-levy")

  def getRosmRegistration(utr: String)(implicit hc: HeaderCarrier): Future[Option[RosmRegistration]] =
    http.GET[Option[RosmRegistration]](s"$sdilUrl/rosm-registration/lookup/$utr")

  def submit(subscription: Subscription, safeId: String)(implicit hc: HeaderCarrier): Future[Unit] =
    http.POST[Subscription, HttpResponse](s"$sdilUrl/subscription/utr/${subscription.utr}/$safeId", subscription) map {
      _ =>
        ()
    }

  def checkPendingQueue(utr: String)(implicit hc: HeaderCarrier): Future[HttpResponse] =
    http.GET[HttpResponse](s"$sdilUrl/check-enrolment-status/$utr") recover {
      case e: UpstreamErrorResponse if e.statusCode == 404 => HttpResponse(404, "")
    }

  def checkDirectDebitStatus(sdilRef: String)(implicit hc: HeaderCarrier): Future[Boolean] =
    http.GET[JsObject](s"$sdilUrl/check-direct-debit-status/$sdilRef").map { result =>
      result.value("directDebitMandateFound").as[Boolean]
    }

  def retrieveSubscription(sdilNumber: String, identifierType: String = "sdil")(
    implicit hc: HeaderCarrier): Future[Option[RetrievedSubscription]] =
    sessionCache.fetchAndGetEntry[RetrievedSubscription](s"sdil-$sdilNumber") flatMap {
      case Some(s) => Future.successful(Some(s))
      case _ =>
        http.GET[Option[RetrievedSubscription]](s"$sdilUrl/subscription/$identifierType/$sdilNumber").flatMap {
          case Some(a) =>
            sessionCache.cache(s"sdil-$sdilNumber", a).map { _ =>
              Some(a)
            }
          case _ => Future.successful(None)
        }
    }

  def checkSmallProducerStatus(
    sdilRef: String,
    period: ReturnPeriod
  )(implicit hc: HeaderCarrier): Future[Option[Boolean]] =
    sessionCache.fetchAndGetEntry[Boolean](s"sdil-$sdilRef-${period.year}-${period.quarter}") flatMap {
      case Some(s) => Future.successful(Some(s))
      case _ =>
        http
          .GET[Option[Boolean]](s"$sdilUrl/subscriptions/sdil/$sdilRef/year/${period.year}/quarter/${period.quarter}")
          .flatMap {
            case Some(a) =>
              sessionCache.cache(s"sdil-$sdilRef-${period.year}-${period.quarter}", a).map { _ =>
                Some(a)
              }
            case _ => Future.successful(None)
          }
    }

  def submitVariation(variation: VariationsSubmission, sdilNumber: String)(implicit hc: HeaderCarrier): Future[Unit] = {
    Future.successful(println(Console.YELLOW + variation + Console.WHITE))
    http.POST[VariationsSubmission, HttpResponse](s"$sdilUrl/submit-variations/sdil/$sdilNumber", variation) map { _ =>
      ()
    }
  }

  def returns_pending(
    utr: String
  )(implicit hc: HeaderCarrier): Future[List[ReturnPeriod]] =
    http.GET[List[ReturnPeriod]](s"$sdilUrl/returns/$utr/pending")

  def returns_variable(
    utr: String
  )(implicit hc: HeaderCarrier): Future[List[ReturnPeriod]] =
    http.GET[List[ReturnPeriod]](s"$sdilUrl/returns/$utr/variable")

  def returns_vary(
    sdilRef: String,
    data: ReturnVariationData
  )(implicit hc: HeaderCarrier): Future[Unit] = {
    val uri = s"$sdilUrl/returns/vary/$sdilRef"
    http.POST[ReturnVariationData, HttpResponse](uri, data) map { _ =>
      ()
    }
  }

  def returns_update(
    utr: String,
    period: ReturnPeriod,
    sdilReturn: SdilReturn
  )(implicit hc: HeaderCarrier): Future[Unit] = {
    val uri = s"$sdilUrl/returns/$utr/year/${period.year}/quarter/${period.quarter}"
    http.POST[SdilReturn, HttpResponse](uri, sdilReturn) map { _ =>
      ()
    }
  }

  def returns_get(
    utr: String,
    period: ReturnPeriod
  )(implicit hc: HeaderCarrier): Future[Option[SdilReturn]] = {
    val uri = s"$sdilUrl/returns/$utr/year/${period.year}/quarter/${period.quarter}"
    http.GET[Option[SdilReturn]](uri)
  }

  def returns_variation(variation: ReturnsVariation, sdilRef: String)(implicit hc: HeaderCarrier): Future[Unit] =
    http.POST[ReturnsVariation, HttpResponse](s"$sdilUrl/returns/variation/sdil/$sdilRef", variation) map { _ =>
      ()
    }

  def balance(
    sdilRef: String,
    withAssessment: Boolean
  )(implicit hc: HeaderCarrier): Future[BigDecimal] =
    http.GET[BigDecimal](s"$sdilUrl/balance/$sdilRef/$withAssessment")

  def balanceHistory(
    sdilRef: String,
    withAssessment: Boolean
  )(implicit hc: HeaderCarrier): Future[List[FinancialLineItem]] = {
    import FinancialLineItem.formatter
    http.GET[List[FinancialLineItem]](s"$sdilUrl/balance/$sdilRef/history/all/$withAssessment")
  }

}
