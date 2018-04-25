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

import play.api.libs.json._
import sdil.models.Address
import sdil.models.backend.Activity

object VariationsSubmission {
  implicit val writes: Writes[VariationsSubmission] = Json.writes[VariationsSubmission]
}

/** The payload that is sent to GForms */
case class VariationsSubmission(tradingName: Option[String] = None,
                                businessContact: Option[VariationsContact],
                                correspondenceContact: Option[VariationsContact],
                                primaryPersonContact: Option[VariationsPersonalDetails],
                                sdilActivity: Option[SdilActivity],
                                deregistrationText: Option[String] = None,
                                newSites: List[VariationsSite] = Nil,
                                amendSites: List[VariationsSite] = Nil,
                                closeSites: List[CloseSites] = Nil)

object VariationsContact {
  implicit val writes: Writes[VariationsContact] = new Writes[VariationsContact] {
    override def writes(o: VariationsContact): JsValue = Json.obj(
      "addressLine1" -> o.address.map(_.line1),
      "addressLine2" -> o.address.map(_.line2),
      "addressLine3" -> o.address.map(_.line3),
      "addressLine4" -> o.address.map(_.line4),
      "postCode" -> o.address.map(_.postcode),
      "telephoneNumber" -> o.telephoneNumber,
      "emailAddress" -> o.emailAddress
    )
  }
}

case class VariationsContact(address: Option[Address] = None,
                             telephoneNumber: Option[String] = None,
                             emailAddress: Option[String] = None) {
  def nonEmpty: Boolean = Seq(address, telephoneNumber, emailAddress).flatten.nonEmpty
}

object VariationsPersonalDetails {
  implicit val writes: Writes[VariationsPersonalDetails] = Json.writes[VariationsPersonalDetails]
}

case class VariationsPersonalDetails(name: Option[String] = None,
                                     position: Option[String] = None,
                                     telephoneNumber: Option[String] = None,
                                     emailAddress: Option[String] = None) {
  def nonEmpty: Boolean = Seq(name, position, telephoneNumber, emailAddress).flatten.nonEmpty
}

object SdilActivity {

  implicit val writes: Writes[SdilActivity] = Json.writes[SdilActivity]
}

case class SdilActivity(activity: Option[Activity],
                        produceLessThanOneMillionLitres: Option[Boolean],
                        smallProducerExemption: Option[Boolean], //If true then the user does not have to file returns
                        usesContractPacker: Option[Boolean],
                        voluntarilyRegistered: Option[Boolean],
                        reasonForAmendment: Option[String],
                        taxObligationStartDate: Option[LocalDate]) {
  def nonEmpty: Boolean = Seq(activity, reasonForAmendment, taxObligationStartDate).flatten.nonEmpty
}

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