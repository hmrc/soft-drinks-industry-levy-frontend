/*
 * Copyright 2017 HM Revenue & Customs
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

package sdil.forms

import play.api.data.Forms._
import play.api.data.Mapping
import sdil.models.Address

trait FormHelpers {
  private lazy val postcodeRegex = """([Gg][Ii][Rr] 0[Aa]{2})|((([A-Za-z][0-9]{1,2})|(([A-Za-z][A-Ha-hJ-Yj-y][0-9]{1,2})|(([A-Za-z][0-9][A-Za-z])|([A-Za-z][A-Ha-hJ-Yj-y][0-9]?[A-Za-z]))))\s?[0-9][A-Za-z]{2})"""

  lazy val postcode: Mapping[String] = text.verifying("error.postcode.invalid", _.matches(postcodeRegex))

  lazy val addressMapping: Mapping[Address] = mapping(
    "line1" -> nonEmptyText,
    "line2" -> text,
    "line3" -> text,
    "line4" -> text,
    "postcode" -> postcode
  )(Address.apply)(Address.unapply)

  lazy val mandatoryBoolean: Mapping[Boolean] = optional(boolean).verifying("error.required", _.nonEmpty).transform(_.get, Some.apply)
}
