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

import com.google.inject.{AbstractModule, Provides}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.crypto.{CompositeSymmetricCrypto, CryptoGCMWithKeysFromConfig}

class ModuleBindings(val environment: Environment, val configuration: Configuration) extends AbstractModule {

  @Provides def provideCrypto(config: Configuration): CompositeSymmetricCrypto =
    new CryptoGCMWithKeysFromConfig(baseConfigKey = "json.encryption", config.underlying)

  override def configure() = {}
//    bind[client.ShortLivedHttpCaching].to[DefaultShortLivedHttpCaching]
}
