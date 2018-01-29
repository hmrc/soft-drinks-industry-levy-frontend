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

import play.api.data.FormError
import sdil.controllers.ProductionSiteController.{ProductionSites, form}
import sdil.models.Address

class ProductionSiteFormSpec extends FormSpec {

  "The production site form" should {
    "require the postcode if there is another site" in {
      val f = form.bind(productionSitesData - postcode)

      mustContainError(f, postcode, "error.required")
    }

    "require the first address line if there is another site" in {
      val f = form.bind(productionSitesData - line1)

      mustContainError(f, line1, "error.required")
    }

    "require at least one production site to be selected" in {
      val f = form.bind(Map.empty[String, String])

      f.errors mustBe Seq(FormError("", "error.no-production-sites"))
    }

    "bind to ProductionSites if there is not another site" in {
      val f = form.bind(productionSitesData - addSite - line1 - "additionalSite.line2" - "additionalSite.line3" - "additionalSite.line4" - postcode)

      f.value mustBe Some(ProductionSites(Some("1,1,AA11 1AA"), Some("2,2,AA22 2AA"), Nil, false, None))
    }

    "bind to ProductionSites if there is another site and an address is provided" in {
      val f = form.bind(productionSitesData)

      f.value mustBe Some(ProductionSites(Some("1,1,AA11 1AA"), Some("2,2,AA22 2AA"), Nil, true, Some(Address("line 1", "line 2", "line 3", "line 4", "AA11 1AA"))))
    }
  }

  lazy val productionSitesData = Map(
    "bprAddress" -> "1,1,AA11 1AA",
    "ppobAddress" -> "2,2,AA22 2AA",
    addSite -> "true",
    line1 -> "line 1",
    "additionalAddress.line2" -> "line 2",
    "additionalAddress.line3" -> "line 3",
    "additionalAddress.line4" -> "line 4",
    postcode -> "AA11 1AA"
  )
  lazy val addSite = "addAddress"
  lazy val line1 = "additionalAddress.line1"
  lazy val postcode = "additionalAddress.postcode"
}
