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

package sdil.controllers

import org.scalatestplus.play.PlaySpec
import play.api.data.Form

trait FormSpec extends PlaySpec {
  def mustContainError(f: Form[_], fieldName: String, expectedError: String) = {
    if(f(fieldName).hasErrors) {
      f(fieldName).error.value.message mustBe expectedError
    } else {
      fail(s"No error for $fieldName; actual errors: ${f.errors}")
    }
  }
}
