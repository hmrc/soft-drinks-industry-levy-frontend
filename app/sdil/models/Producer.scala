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

package sdil.models

import play.api.libs.json._
import play.api.libs.functional.syntax._

case class Producer(isProducer: Boolean, isLarge: Option[Boolean])

object Producer {
  implicit val format: Format[Producer] = (
    (__ \ "isProducer").format[Boolean] and
      (__ \ "isLarge").formatNullable[Boolean]
    )(Producer.apply, unlift(Producer.unapply))
}
