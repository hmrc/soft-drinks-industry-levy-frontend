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

import sdil.controllers.WarehouseController.{SecondaryWarehouses, form}
import sdil.models.Address

class WarehouseFormSpec extends FormSpec {

  "The warehouse form" should {
    "bind to SecondaryWarehouses if there are no additional warehouses" in {
      val f = form.bind(Map(addWarehouse -> "false"))

      f.value mustBe Some(SecondaryWarehouses(Nil, false, None))
    }

    "validate the address if there is a warehouse" in {
      mustValidateAddress(form, "additionalAddress", secondaryWarehouseData)
    }

    "bind to SecondaryWarehouses if there is a warehouse and an address is provided" in {
      val f = form.bind(secondaryWarehouseData)

      f.value mustBe Some(SecondaryWarehouses(Nil, true, Some(Address("line 1", "line 2", "", "", "AA11 1AA"))))
    }
  }

  lazy val secondaryWarehouseData = Map(
    addWarehouse -> "true",
    line1 -> "line 1",
    line2 -> "line 2",
    "additionalAddress.line3" -> "",
    "additionalAddress.line4" -> "",
    postcode -> "AA11 1AA"
  )

  lazy val addWarehouse = "addWarehouse"
  lazy val line1 = "additionalAddress.line1"
  lazy val line2 = "additionalAddress.line2"
  lazy val postcode = "additionalAddress.postcode"
}
