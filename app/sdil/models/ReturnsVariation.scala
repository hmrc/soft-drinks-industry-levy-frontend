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

package sdil.models

import play.api.libs.json.{Format, Json}
import sdil.models.backend.{Site, UkAddress}

case class ReturnsVariation(
  orgName: String,
  ppobAddress: UkAddress,
  importer: (Boolean, (Long, Long)) = (false, (0, 0)),
  packer: (Boolean, (Long, Long)) = (false, (0, 0)),
  warehouses: List[Site] = Nil,
  packingSites: List[Site] = Nil,
  phoneNumber: String,
  email: String,
  taxEstimation: BigDecimal)
object ReturnsVariation {
  implicit val bllFormat: Format[(Boolean, (Long, Long))] = Json.format[(Boolean, (Long, Long))]
  implicit val format: Format[ReturnsVariation] = Json.format[ReturnsVariation]
}
