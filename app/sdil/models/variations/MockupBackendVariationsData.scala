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

import java.time.LocalDate

import play.api.libs.json.{Json, Writes}
import sdil.models.backend.{Activity, UkAddress}

object VariationsSubmission {
  implicit val writes: Writes[VariationsSubmission] = Json.writes[VariationsSubmission]
}

/** The payload that is sent to GForms */
case class VariationsSubmission(tradingName: Option[String] = None,
                                businessContact: VariationsContact,
                                correspondenceContact: VariationsContact,
                                primaryPersonContact: VariationsPersonalDetails,
                                sdilActivity: SdilActivity,
                                deregistrationText: Option[String] = None,
                                newSites: List[VariationsSite] = Nil,
                                amendSites: List[VariationsSite] = Nil,
                                closeSites: List[CloseSites] = Nil)

object VariationsContact {
  implicit val writes: Writes[VariationsContact] = Json.writes[VariationsContact]
}

case class VariationsContact(address: Option[UkAddress] = None,
                             telephoneNumber: Option[String] = None,
                             emailAddress: Option[String] = None)

object VariationsPersonalDetails {
  implicit val writes: Writes[VariationsPersonalDetails] = Json.writes[VariationsPersonalDetails]
}

case class VariationsPersonalDetails(name: Option[String] = None,
                                     position: Option[String] = None,
                                     telephoneNumber: Option[String] = None,
                                     emailAddress: Option[String] = None)

object SdilActivity {

  implicit val writes: Writes[SdilActivity] = Json.writes[SdilActivity]
}

case class SdilActivity(activity: Activity,
                        reasonForAmendment: Option[String] = None,
                        taxObligationStartDate: Option[LocalDate] = None)

object VariationsSite {
  implicit val writes: Writes[VariationsSite] = Json.writes[VariationsSite]
}

case class VariationsSite(tradingName: String,
                          siteReference: String,
                          variationsContact: VariationsContact,
                          typeOfSite: String)

object CloseSites {
  implicit val writes: Writes[CloseSites] = Json.writes[CloseSites]
}

case class CloseSites(tradingName: String,
                      siteReference: String,
                      reasonOfClosure: String)