/*
 * Copyright 2024 HM Revenue & Customs
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

import play.api.libs.functional.syntax._
import play.api.libs.json._

case class RosmRegistration(
  safeId: String,
  organisation: Option[OrganisationDetails],
  individual: Option[IndividualDetails],
  address: Address) {

  lazy val organisationName: String = {
    organisation.map(_.organisationName).orElse(individual.map(i => s"${i.firstName} ${i.lastName}")).getOrElse("")
  }
}

object RosmRegistration {
  private val addressReads: Reads[Address] = (
    (__ \ "addressLine1").read[String] and
      (__ \ "addressLine2").read[String].orElse(Reads.pure("")) and
      (__ \ "addressLine3").read[String].orElse(Reads.pure("")) and
      (__ \ "addressLine4").read[String].orElse(Reads.pure("")) and
      (__ \ "postalCode").read[String]
  )(Address.apply _)

  private val addressWrites: Writes[Address] = (
    (__ \ "addressLine1").write[String] and
      (__ \ "addressLine2").write[String] and
      (__ \ "addressLine3").write[String] and
      (__ \ "addressLine4").write[String] and
      (__ \ "postalCode").write[String]
  )(unlift(Address.unapply))

  private implicit val addressFormat: Format[Address] = Format(addressReads, addressWrites)

  implicit val format: Format[RosmRegistration] = Json.format[RosmRegistration]
}

case class OrganisationDetails(organisationName: String)

object OrganisationDetails {
  implicit val format: Format[OrganisationDetails] = Json.format[OrganisationDetails]
}

case class IndividualDetails(firstName: String, lastName: String)

object IndividualDetails {
  implicit val format: Format[IndividualDetails] = Json.format[IndividualDetails]
}
