/*
 * Copyright 2023 HM Revenue & Customs
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

import org.scalatestplus.play.PlaySpec
import play.api.data.Form

trait FormSpec extends PlaySpec {
  def mustContainError(f: Form[_], fieldName: String, expectedError: String) =
    if (f(fieldName).hasErrors) {
      f(fieldName).error.value.message mustBe expectedError
    } else {
      fail(s"No error when $fieldName = ${f(fieldName).value}; actual errors: ${f.errors}")
    }

  def mustContainNoError(f: Form[_], fieldName: String) =
    assert(
      f(fieldName).errors.isEmpty,
      s"Unexpected errors when $fieldName = ${f(fieldName).value}\nErrors: ${f(fieldName).errors}")

  def mustValidateAddress(form: Form[_], prefix: String, data: Map[String, String]) = {
    mustRequire(s"$prefix.line1")(form, data, requiredError = "line1.required")
    mustRequire(s"$prefix.line2")(form, data, requiredError = "line2.required")
    mustNotRequire(s"$prefix.line3")(form, data)
    mustNotRequire(s"$prefix.line4")(form, data)
    mustRequire(s"$prefix.postcode")(form, data, requiredError = "postcode.empty")

    Seq("line1", "line2", "line3", "line4") map { l =>
      val line = s"$prefix.$l"

      mustHaveMaxLength(line, 35)(form, data, s"error.$l.over")

      val validLine = "Aa0-,.&'/"
      mustContainNoError(form.bind(data.updated(line, validLine)), line)

      val invalidLine = "the;place"
      mustContainError(form.bind(data.updated(line, invalidLine)), line, s"error.$l.invalid")
    }
  }

  def mustRequire(fieldName: String)(f: Form[_], data: Map[String, String], requiredError: String = "error.required") =
    mustContainError(f.bind(data.updated(fieldName, "")), fieldName, requiredError)

  def mustNotRequire(fieldName: String)(f: Form[_], data: Map[String, String]) =
    mustContainNoError(f.bind(data.updated(fieldName, "")), fieldName)

  def mustHaveMaxLength(fieldName: String, max: Int)(f: Form[_], data: Map[String, String], expectedError: String) = {
    val underMax = (1 until max).map(_ => 'A').mkString
    mustContainNoError(f.bind(data.updated(fieldName, underMax)), fieldName)

    val equalToMax = (1 to max).map(_ => 'B').mkString
    mustContainNoError(f.bind(data.updated(fieldName, equalToMax)), fieldName)

    val overMax = (1 to max + 1).map(_ => 'C').mkString
    mustContainError(f.bind(data.updated(fieldName, overMax)), fieldName, expectedError)
  }
}
