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

import sdil.models.Litreage

class LitreageFormSpec extends FormSpec {

  "The litreage form" should {
    "require the lower rate volume" in {
      val f = LitreageForm().bind(validData - lowerRate)

      mustContainError(f, lowerRate, "error.required")
    }

    "require the higher rate volume" in {
      val f = LitreageForm().bind(validData - higherRate)

      mustContainError(f, higherRate, "error.required")
    }

    "require the lower rate volume to be a number" in {
      val f = LitreageForm().bind(validData.updated(lowerRate, "not a number"))

      mustContainError(f, lowerRate, "error.number")
    }

    "require the higher rate volume to be a number" in {
      val f = LitreageForm().bind(validData.updated(higherRate, "not a number"))

      mustContainError(f, higherRate, "error.number")
    }

    "require the lower rate volume to be positive" in {
      val f = LitreageForm().bind(validData.updated(lowerRate, "-1"))

      mustContainError(f, lowerRate, "error.number.negative")
    }

    "require the higher rate volume to be positive" in {
      val f = LitreageForm().bind(validData.updated(higherRate, "-1"))

      mustContainError(f, higherRate, "error.number.negative")
    }

    "require the lower rate volume to be less than 10,000,000,000,000" in {
      val f = LitreageForm().bind(validData.updated(lowerRate, "10000000000000"))

      mustContainError(f, lowerRate, "error.litreage.max")
    }

    "require the higher rate volume to be less than 10,000,000,000,000" in {
      val f = LitreageForm().bind(validData.updated(higherRate, "10000000000000"))

      mustContainError(f, higherRate, "error.litreage.max")
    }

    "bind to Litreage if the lower and higher rate volumes are valid" in {
      val f = LitreageForm().bind(validData)

      f.value mustBe Some(Litreage(1, 2))
    }
  }

  lazy val lowerRate = "lowerRateLitres"
  lazy val higherRate = "higherRateLitres"

  lazy val validData = Map(lowerRate -> "1", higherRate -> "2")
}
