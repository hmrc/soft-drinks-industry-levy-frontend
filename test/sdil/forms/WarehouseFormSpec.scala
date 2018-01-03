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

import sdil.models.{Address, SecondaryWarehouse}
import sdil.controllers.WarehouseController.form

class WarehouseFormSpec extends FormSpec {

  "The warehouse form" should {
    "require a 'do you have a warehouse' option to be selected" in {
      val f = form.bind(secondaryWarehouseData - hasWarehouse)

      mustContainError(f, hasWarehouse, "error.radio-form.choose-option")
    }

    "bind to SecondaryWarehouse if there is no warehouse" in {
      val f = form.bind(Map(hasWarehouse -> "false"))

      f.value mustBe Some(SecondaryWarehouse(false, None))
    }

    "bind to SecondaryWarehouse if there is a warehouse and an address is provided" in {
      val f = form.bind(secondaryWarehouseData)

      f.value mustBe Some(SecondaryWarehouse(true, Some(Address("line 1", "line 2", "", "", "AA11 1AA"))))
    }
  }

  lazy val secondaryWarehouseData = Map(
    hasWarehouse -> "true",
    line1 -> "line 1",
    line2 -> "line 2",
    "warehouseAddress.line3" -> "",
    "warehouseAddress.line4" -> "",
    postcode -> "AA11 1AA"
  )
  lazy val hasWarehouse = "hasWarehouse"
  lazy val line1 = "warehouseAddress.line1"
  lazy val line2 = "warehouseAddress.line2"
  lazy val postcode = "warehouseAddress.postcode"
}
