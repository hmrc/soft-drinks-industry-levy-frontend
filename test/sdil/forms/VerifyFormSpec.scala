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

import sdil.models.{Address, DetailsCorrect}
import sdil.controllers.VerifyController.form

class VerifyFormSpec extends FormSpec {

  "The verify form" should {
    "require a 'details correct' selection" in {
      val f = form.bind(altAddressData - detailsCorrect)

      mustContainError(f, detailsCorrect, "error.radio-form.choose-option")
    }

    "reject invalid 'details correct' options" in {
      val f = form.bind(altAddressData.updated(detailsCorrect, "maybe"))

      mustContainError(f, detailsCorrect, "error.radio-form.choose-option")
    }

    "validate the address if the user wants to register a different address" in {
      mustValidateAddress(form, "alternativeAddress", altAddressData)
    }

    "bind to VerifiedDetails if the details are correct and an alternative address is not provided" in {
      val f = form.bind(Map(detailsCorrect -> "yes"))

      f.value mustBe Some(DetailsCorrect.Yes)
    }

    "bind to VerifiedDetails if the user wants to register a different address, and the first line and postcode are provided" in {
      val f = form.bind(altAddressData)

      f.value mustBe Some(DetailsCorrect.DifferentAddress(Address("some street", "some town", "", "", "AA11 1AA")))
    }

    "bind to VerifiedDetails if the details are wrong and an alternative address is not provided" in {
      val f = form.bind(Map(detailsCorrect -> "no"))

      f.value mustBe Some(DetailsCorrect.No)
    }
  }

  lazy val detailsCorrect = "detailsCorrect"
  lazy val line1 = "alternativeAddress.line1"
  lazy val line2 = "alternativeAddress.line2"
  lazy val postcode = "alternativeAddress.postcode"

  lazy val altAddressData = Map(
    detailsCorrect -> "differentAddress",
    line1 -> "some street",
    line2 -> "some town",
    "alternativeAddress.line3" -> "",
    "alternativeAddress.line4" -> "",
    postcode -> "AA11 1AA"
  )
}
