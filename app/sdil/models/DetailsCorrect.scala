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

import play.api.libs.json._

sealed trait DetailsCorrect

object DetailsCorrect {
  case object Yes extends DetailsCorrect
  case class DifferentAddress(address: Address) extends DetailsCorrect
  case object No extends DetailsCorrect

  lazy val options = Seq("yes", "differentAddress", "no")

  def apply(value: String, address: Option[Address]): DetailsCorrect = (value, address) match {
    case (_, Some(addr)) => DifferentAddress(addr)
    case ("yes", _)      => Yes
    case _               => No
  }

  def unapply(d: DetailsCorrect): Option[(String, Option[Address])] = d match {
    case Yes                    => Some(("yes", None))
    case DifferentAddress(addr) => Some(("differentAddress", Some(addr)))
    case No                     => Some(("no", None))
  }

  implicit val format: Format[DetailsCorrect] = new Format[DetailsCorrect] {
    override def reads(json: JsValue): JsResult[DetailsCorrect] = json match {
      case JsString("yes") => JsSuccess(Yes)
      case JsString("no")  => JsSuccess(No)
      case _: JsObject     => (json \ "alternativeAddress").validate[Address].map(DifferentAddress)
      case _               => JsError(s"Unable to parse $json into DetailsCorrect")
    }

    override def writes(o: DetailsCorrect): JsValue = o match {
      case Yes                       => JsString("yes")
      case DifferentAddress(address) => Json.obj("alternativeAddress" -> address)
      case No                        => JsString("no")
    }
  }
}
