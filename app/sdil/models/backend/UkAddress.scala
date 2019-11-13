/*
 * Copyright 2019 HM Revenue & Customs
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

package sdil.models.backend

import play.api.libs.json.{Format, JsResult, JsValue, Json}
import sdil.models.Address

trait RetrievedAddress {
  def lines: List[String]
  def country: String
}

object RetrievedAddress {
  implicit val format: Format[RetrievedAddress] = new Format[RetrievedAddress] {
    override def writes(o: RetrievedAddress): JsValue = o match {
      case uk: UkAddress           => Json.toJson(uk)
      case foreign: ForeignAddress => Json.toJson(foreign)
    }

    override def reads(json: JsValue): JsResult[RetrievedAddress] =
      (json \ "country").asOpt[String].map(_.toLowerCase) match {
        case Some("uk") | None => json.validate[UkAddress]
        case _                 => json.validate[ForeignAddress]
      }
  }
}

case class UkAddress(lines: List[String], postCode: String) extends RetrievedAddress {
  val country = "GB"
}

object UkAddress {
  implicit val ukAddressFormat: Format[UkAddress] = Json.format[UkAddress]

  def fromAddress(address: Address): UkAddress =
    UkAddress(
      List(address.line1, address.line2, address.line3, address.line4).filter(_.nonEmpty),
      address.postcode
    )
}

case class ForeignAddress(lines: List[String], country: String) extends RetrievedAddress

object ForeignAddress {
  implicit val foreignAddressFormat: Format[ForeignAddress] = Json.format[ForeignAddress]
}
