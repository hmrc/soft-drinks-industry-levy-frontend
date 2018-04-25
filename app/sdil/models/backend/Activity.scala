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

import play.api.libs.json.{Format, JsResult, JsValue, Json}
import sdil.models.Litreage

case class Activity(ProducedOwnBrand: Option[Litreage],
                    Imported: Option[Litreage],
                    CopackerAll: Option[Litreage],
                    Copackee: Option[Litreage],
                    isLarge: Boolean) {

  def nonEmpty: Boolean = Seq(ProducedOwnBrand, Imported, CopackerAll, Copackee).flatten.nonEmpty
}

object Activity {
  private implicit val litreageFormat: Format[Litreage] = new Format[Litreage] {
    override def writes(o: Litreage): JsValue = Json.obj("lower" -> o.atLowRate, "upper" -> o.atHighRate)

    override def reads(json: JsValue): JsResult[Litreage] = {
      for {
        low <- (json \ "lower").validate[BigDecimal]
        high <- (json \ "upper").validate[BigDecimal]
      } yield {
        Litreage(low, high)
      }
    }
  }

  implicit val format: Format[Activity] = Json.format[Activity]
}

