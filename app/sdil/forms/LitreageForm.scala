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

import play.api.data.Form
import play.api.data.Forms._
import sdil.models.Litreage

object LitreageForm {
  def apply(): Form[Litreage] = Form(
    mapping(
      "lowerRateLitres" -> litreage,
      "higherRateLitres" -> litreage
    )(Litreage.apply)(Litreage.unapply))

  private lazy val litreage = {
    longNumber
      .verifying("error.litreage.max", _ < 10000000000000L)
      .verifying("error.number.negative", _ >= 0)
  }
}