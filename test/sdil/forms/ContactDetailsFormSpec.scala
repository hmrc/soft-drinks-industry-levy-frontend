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

package sdil.forms

import sdil.controllers.ContactDetailsController.form
import sdil.models.ContactDetails

class ContactDetailsFormSpec extends FormSpec {

  "The contact details form" should {
    "require a full name" in {
      mustRequire(keys.fullName)(form, validData, "error.fullName.required")
    }

    "require a position" in {
      mustRequire(keys.position)(form, validData, "error.position.required")
    }

    "require a phone number" in {
      mustRequire(keys.phoneNumber)(form, validData, "error.phoneNumber.required")
    }

    "require the phone number to be no more than 24 characters" in {
      mustHaveMaxLength(keys.phoneNumber, 24)(form, validData, "error.phoneNumber.length")
    }

    "require the phone number to only contain numbers, spaces, and ()+,-" in {
      val valid = "123 ()+,-"
      mustContainNoError(form.bind(validData.updated(keys.phoneNumber, valid)), keys.phoneNumber)

      val invalid = "phone number"
      mustContainError(form.bind(validData.updated(keys.phoneNumber, invalid)), keys.phoneNumber, "error.phoneNumber.invalid")
    }

    "require an email address" in {
      mustRequire(keys.email)(form, validData, "error.email.required")
    }

    "require the email address to be valid" in {
      Seq("not an email", "123456", "aa@@aa.aa") map { invalid =>
        val f = form.bind(validData.updated(keys.email, invalid))
        mustContainError(f, keys.email, "error.email")
      }
    }

    "bind to ContactDetails when the form data is valid" in {
      val f = form.bind(validData)
      f.value mustBe Some(ContactDetails("Somebody", "Something", "999", "somebody@something.com"))
    }
  }

  lazy val keys = new {
    val fullName = "fullName"
    val position = "position"
    val phoneNumber = "phoneNumber"
    val email = "email"
  }

  lazy val validData = Map(
    keys.fullName -> "Somebody",
    keys.position -> "Something",
    keys.phoneNumber -> "999",
    keys.email -> "somebody@something.com"
  )
}
