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

package sdil.models

import java.time.LocalDate

import play.api.libs.json.{Format, Json}

case class RegistrationFormData(rosmData: RosmRegistration,
                                utr: String,
                                verify: Option[DetailsCorrect] = None,
                                orgType: Option[String] = None,
                                packaging: Option[Packaging] = None,
                                packageOwn: Option[Litreage] = None,
                                packageCopack: Option[Litreage] = None,
                                packageCopackSmall: Option[Boolean] = None,
                                packageCopackSmallVol: Option[Litreage] = None,
                                copacked: Option[Boolean] = None,
                                copackedVolume: Option[Litreage] = None,
                                imports: Option[Boolean] = None,
                                importVolume: Option[Litreage] = None,
                                startDate: Option[LocalDate] = None,
                                productionSites: Option[Seq[Address]] = None,
                                secondaryWarehouses: Option[Seq[Address]] = None,
                                contactDetails: Option[ContactDetails] = None)

object RegistrationFormData {
  implicit val format: Format[RegistrationFormData] = Json.format[RegistrationFormData]
}