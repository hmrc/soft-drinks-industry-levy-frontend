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

import sdil.uniform.SdilComponents._

class PostcodeMappingSpec extends FormSpec with FormHelpers {

  "The postcode mapping" should {
    "require the postcode to be non-empty" in {
      expectError("", "postcode.empty")
    }

    "require the postcode to be valid" in {
      Seq("AA11", "A11A 1AA", "not a postcode") map { pc =>
        expectError(pc, "postcode.invalid")
      }
    }

    "accept all valid postcode formats" in {
      Seq("A1 1AA", "A11 1AA", "A1A 1AA", "AA1 1AA", "AA11 1AA", "AA1A 1AA") foreach { mustBind }
    }

    "accept postcodes without spaces" in {
      Seq("A11AA", "AA11AA", "AA111AA", "AA1A1AA", "A1A1AA", "A111AA") foreach { mustBind }
    }

    "accept lower case postcodes" in {
      Seq("a1 1aa", "a11 1aa", "a1a 1aa", "aa1 1aa", "aa11 1aa", "aa1a 1aa") foreach { mustBind }
    }
  }

  private def expectError(value: String, errorMsg: String) =
    postcode.bind(Map("" -> value)) match {
      case Left(errs) => errs.head.message mustBe errorMsg
      case Right(_)   => fail(s"expected error $errorMsg for value $value, but bound successfully")
    }

  private def mustBind(value: String): Unit =
    postcode.bind(Map("" -> value)) match {
      case Left(errs) => fail(s"Unexpected errors $errs for value $value")
      case Right(_)   => //pass
    }
}
