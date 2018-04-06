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

package sdil.models.variations

import play.api.libs.json.{Format, Json}
import sdil.models.Address
import sdil.models.retrieved.RetrievedSubscription

case class VariationData(original: RetrievedSubscription, updatedBusinessDetails: UpdatedBusinessDetails)

object VariationData {
  implicit val format: Format[VariationData] = Json.format[VariationData]

  def apply(original: RetrievedSubscription): VariationData = VariationData(
    original,
    UpdatedBusinessDetails(original.orgName, Address.fromUkAddress(original.address))
  )
}