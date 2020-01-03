/*
 * Copyright 2020 HM Revenue & Customs
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
import play.api.data.validation.{Constraint, Invalid, Valid}
import play.api.libs.json.Json
import sdil.models.backend.Site

trait FormHelpers {

  lazy val siteJsonMapping: Mapping[Site] =
    text.transform[Site](s => Json.parse(s).as[Site], s => Json.toJson(s).toString)

  def required(key: String): Constraint[String] = Constraint {
    case "" => Invalid(s"error.$key.required")
    case _  => Valid
  }

  lazy val mandatoryBoolean: Mapping[Boolean] = optional(boolean)
    .verifying("error.radio-form.choose-option", _.nonEmpty)
    .transform(_.get, Some.apply)

  def combine[T](c1: Constraint[T], c2: Constraint[T]): Constraint[T] = Constraint { v =>
    c1.apply(v) match {
      case Valid      => c2.apply(v)
      case i: Invalid => i
    }
  }
}
