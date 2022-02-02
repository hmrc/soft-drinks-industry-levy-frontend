/*
 * Copyright 2022 HM Revenue & Customs
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
import sdil.models.variations.RegistrationVariationData
import uk.gov.hmrc.crypto.CompositeSymmetricCrypto
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.{CacheMap, ShortLivedCache, ShortLivedHttpCaching}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}

class RegistrationVariationFormDataCache(
  val configuration: Configuration,
  val shortLiveCache: ShortLivedHttpCaching
)(implicit val crypto: CompositeSymmetricCrypto, ec: ExecutionContext)
    extends ServicesConfig(configuration) with ShortLivedCache {

  def cache(internalId: String, body: RegistrationVariationData)(implicit hc: HeaderCarrier): Future[CacheMap] =
    cache(s"$internalId-sdil-registration-variation", "regVarFormData", body)

  def get(internalId: String)(implicit hc: HeaderCarrier): Future[Option[RegistrationVariationData]] =
    fetchAndGetEntry[RegistrationVariationData](s"$internalId-sdil-registration-variation", "regVarFormData").recover {
      case _: JsResultException => None
    }

  def clear(internalId: String)(implicit hc: HeaderCarrier): Future[Unit] =
    remove(s"$internalId-sdil-registration-variation") map { _ =>
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