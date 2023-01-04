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

package sdil.config

import play.api.Configuration
import play.api.libs.json.JsResultException
import sdil.models.RegistrationFormData
import uk.gov.hmrc.crypto.CompositeSymmetricCrypto
import uk.gov.hmrc.http.cache.client.{CacheMap, ShortLivedHttpCaching}
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class RegistrationFormDataCache @Inject()(
  val http: HttpClient,
  val configuration: Configuration
)(implicit val crypto: CompositeSymmetricCrypto, ec: ExecutionContext)
    extends ServicesConfig(configuration) with ShortLivedHttpCaching {

  override lazy val baseUri: String = baseUrl("cacheable.short-lived-cache")
  override lazy val defaultSource: String =
    getConfString("cacheable.short-lived-cache.journey.cache", "soft-drinks-industry-levy-frontend")
  override lazy val domain: String = getConfString(
    "cacheable.short-lived-cache.domain",
    throw new Exception(s"Could not find config 'cacheable.short-lived-cache.domain'"))

  def cache(internalId: String, body: RegistrationFormData)(implicit hc: HeaderCarrier): Future[CacheMap] =
    cache(s"$internalId-sdil-registration", "formData", body)

  def get(internalId: String)(implicit hc: HeaderCarrier): Future[Option[RegistrationFormData]] =
    fetchAndGetEntry[RegistrationFormData](s"$internalId-sdil-registration", "formData").recover {
      case _: JsResultException => None
    }

  def clear(internalId: String)(implicit hc: HeaderCarrier): Future[Unit] =
    remove(s"$internalId-sdil-registration") map { _ =>
      ()
    }

  def clearInternalIdOnly(internalId: String)(implicit hc: HeaderCarrier): Future[Unit] =
    remove(internalId) map { _ =>
      ()
    }

  def clearBySdilNumber(sdilNo: String)(implicit hc: HeaderCarrier): Future[Unit] =
    remove(sdilNo) map { _ =>
      ()
    }

}
