/*
 * Copyright 2024 HM Revenue & Customs
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

case class Warehouse(
  tradingName: String,
  address: Address
) {
  def nonEmptyLines: Seq[String] =
    Seq(tradingName, address.line1, address.line2, address.line3, address.line4, address.postcode).filter(_.nonEmpty)
}

object Warehouse {

  def fromSite(site: Site): Warehouse =
    Warehouse(site.tradingName.getOrElse(""), Address.fromUkAddress(site.address))

  implicit val format: Format[Warehouse] = Json.format[Warehouse]
}
