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

package sdil.forms

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.data.Form
import sdil.controllers.IdentifyController.form
import sdil.models.Identification

class IdentifyFormSpec extends AnyWordSpecLike with Matchers {

  "The identify form" should {
    "require the UTR to be non-empty" in {
      val emptyUtr = validData.updated(utr, "")

      val f = form.bind(emptyUtr)

      errorFor(f, utr) shouldBe "utr.required"
    }

    "reject UTRs with non-numeric characters" in {
      val nonNumericUtr = validData.updated(utr, "utr")

      val f = form.bind(nonNumericUtr)

      errorFor(f, utr) shouldBe "utr.invalid"
    }

    "reject UTRs that are fewer than 10 characters" in {
      val tooShortUtr = validData.updated(utr, "123")

      val f = form.bind(tooShortUtr)

      errorFor(f, utr) shouldBe "utr.length"
    }

    "reject UTRs that are longer than 10 characters" in {
      val tooLongUtr = validData.updated(utr, "12345678900987654321")

      val f = form.bind(tooLongUtr)

      errorFor(f, utr) shouldBe "utr.length"
    }

    "require the postcode to be non-empty" in {
      val emptyPostcode = validData.updated(postcode, "")

      val f = form.bind(emptyPostcode)

      errorFor(f, postcode) shouldBe "postcode.empty"
    }

    "require the postcode to be valid" in {
      Seq("AA11", "A11A 1AA", "not a postcode") map { pc =>
        val f = form.bind(validData.updated(postcode, pc))

        errorFor(f, postcode) shouldBe "postcode.invalid"
      }
    }

    "bind to Identification when the postcode and UTR are valid" in {
      form.bind(validData).value shouldBe Some(Identification("1234567890", "SW1A 1AA"))
    }
  }

  lazy val utr = "utr"
  lazy val postcode = "postcode"
  lazy val validData = Map(utr -> "1234567890", postcode -> "SW1A 1AA")

  private def errorFor(form: Form[_], fieldName: String): String =
    form(fieldName).error.fold("")(_.message)
}
