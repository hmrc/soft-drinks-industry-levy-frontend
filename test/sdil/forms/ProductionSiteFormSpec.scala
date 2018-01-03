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

import sdil.models.{Address, ProductionSite}
import sdil.controllers.ProductionSiteController.form

class ProductionSiteFormSpec extends FormSpec {

  "The production site form" should {
    "require a selection for 'do you have another production site'" in {
      val f = form.bind(otherSiteData - hasOtherSite)

      mustContainError(f, hasOtherSite, "error.radio-form.choose-option")
    }

    "require the postcode is there is another site" in {
      val f = form.bind(otherSiteData - postcode)

      mustContainError(f, postcode, "error.required")
    }

    "bind to ProductionSite if there is not another site" in {
      val f = form.bind(Map(hasOtherSite -> "false"))

      f.value mustBe Some(ProductionSite(false, None))
    }

    "bind to ProductionSite if there is another site and an address is provided" in {
      val f = form.bind(otherSiteData)

      f.value mustBe Some(ProductionSite(true, Some(Address("line 1", "line 2", "line 3", "line 4", "AA11 1AA"))))
    }
  }

  lazy val otherSiteData = Map(
    hasOtherSite -> "true",
    line1 -> "line 1",
    "otherSiteAddress.line2" -> "line 2",
    "otherSiteAddress.line3" -> "line 3",
    "otherSiteAddress.line4" -> "line 4",
    postcode -> "AA11 1AA"
  )
  lazy val hasOtherSite = "hasOtherSite"
  lazy val line1 = "otherSiteAddress.line1"
  lazy val postcode = "otherSiteAddress.postcode"
}
