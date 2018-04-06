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

package sdil.config

import play.api.Mode.Mode
import play.api.{Configuration, Environment}
import sdil.models.RegistrationFormData
import uk.gov.hmrc.crypto.CompositeSymmetricCrypto
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.{CacheMap, ShortLivedCache, ShortLivedHttpCaching}
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails

import scala.concurrent.Future

class RegistrationFormDataCache(val runModeConfiguration: Configuration,
                                val shortLiveCache: ShortLivedHttpCaching,
                                environment: Environment)
                               (implicit val crypto: CompositeSymmetricCrypto)
  extends ShortLivedCache with ServicesConfig {

  override protected def mode: Mode = environment.mode

  def cache(internalId: String, body: RegistrationFormData)
           (implicit hc: HeaderCarrier): Future[CacheMap] = {
    cache(s"$internalId-sdil-registration", "formData", body)
  }

  def get(internalId: String)
         (implicit hc: HeaderCarrier): Future[Option[RegistrationFormData]] = {
    fetchAndGetEntry[RegistrationFormData](s"$internalId-sdil-registration", "formData")
  }

  def clear(internalId: String)(implicit hc: HeaderCarrier): Future[Unit] = {
    remove(s"$internalId-sdil-registration") map { _ => () }
  }
}
