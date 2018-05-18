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

import play.api.data.Form
import play.api.data.Forms.{boolean, ignored, mapping, seq}
import sdil.models.Sites
import sdil.models.backend.WarehouseSite
import uk.gov.voa.play.form.ConditionalMappings._

object WarehouseForm extends FormHelpers {

  def apply(): Form[Sites[WarehouseSite]] = Form(mapping(
    "additionalSites" -> seq(warehouseSiteJsonMapping),
    "addAddress" -> boolean,
    "tradingName" -> mandatoryIfTrue("addAddress", tradingNameMapping),
    "additionalAddress" -> mandatoryIfTrue("addAddress", addressMapping)
  )(Sites.apply)(Sites.unapply))

  def initial(): Form[Sites[WarehouseSite]] = Form(
    mapping(
      "additionalSites" -> ignored(Seq.empty[WarehouseSite]),
      "addAddress" -> mandatoryBoolean,
      "tradingName" -> mandatoryIfTrue("addAddress", tradingNameMapping),
      "additionalAddress" -> mandatoryIfTrue("addAddress", addressMapping)
    )(Sites.apply)(Sites.unapply)
  )
}
