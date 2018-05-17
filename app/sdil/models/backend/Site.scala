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

package sdil.models.backend

import java.time.LocalDate

import play.api.libs.json.{Format, Json}
import sdil.models.Address

trait Site {
  def address: UkAddress
  def ref: Option[String]
  def tradingName: Option[String]
  def closureDate: Option[LocalDate]
  def getLines: List[String] = {
    tradingName.fold(address.lines) { x => (x :: address.lines) :+ address.postCode}
  }
}

trait FromAddress {
  def fromAddress(address: Address): Site
}

case class PackagingSite(
                        address: UkAddress,
                        ref: Option[String],
                        tradingName: Option[String],
                        closureDate: Option[LocalDate]
                        ) extends Site

case object PackagingSite extends FromAddress {
  implicit val format: Format[PackagingSite] = Json.format[PackagingSite]
  override def fromAddress(address: Address): PackagingSite = {
    PackagingSite(UkAddress.fromAddress(address), None, None, None)
  }
}

case class WarehouseSite(
                        address: UkAddress,
                        ref: Option[String],
                        tradingName: Option[String],
                        closureDate: Option[LocalDate]
                        ) extends Site

case object WarehouseSite extends FromAddress {
  implicit val format: Format[WarehouseSite] = Json.format[WarehouseSite]
  override def fromAddress(address: Address): WarehouseSite = {
    WarehouseSite(UkAddress.fromAddress(address), None, None, None)
  }
}
