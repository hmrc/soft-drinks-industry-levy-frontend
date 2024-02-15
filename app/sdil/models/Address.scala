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

import play.api.libs.json.{Json, OFormat}
import sdil.models.backend.UkAddress

case class Address(line1: String, line2: String, line3: String, line4: String, postcode: String) {
  def nonEmptyLines: Seq[String] = Seq(line1, line2, line3, line4, postcode).filter(_.nonEmpty)
}

object Address {
  def fromString(s: String): Address = {
    def getLine(n: Int) = lines.init.lift(n).getOrElse("")
    lazy val lines = s.split(",")

    Address(getLine(0), getLine(1), getLine(2), getLine(3), lines.lastOption.getOrElse(""))
  }

  def fromUkAddress(address: UkAddress): Address = {
    def getLine(n: Int) = address.lines.lift(n).getOrElse("")

    Address(getLine(0), getLine(1), getLine(2), getLine(3), address.postCode)
  }

  implicit val address: OFormat[Address] = Json.format[Address]
}
