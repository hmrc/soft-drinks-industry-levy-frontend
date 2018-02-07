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

package sdil.models.backend

import java.time.LocalDate

import play.api.libs.json.{Format, Json}
import sdil.models.RegistrationFormData

case class Subscription(utr: String,
                        orgName: String,
                        orgType: Option[String],
                        address: UkAddress,
                        activity: Activity,
                        liabilityDate: LocalDate,
                        productionSites: Seq[Site],
                        warehouseSites: Seq[Site],
                        contact: Contact)

object Subscription {
  implicit val format: Format[Subscription] = Json.format[Subscription]

  def fromFormData(formData: RegistrationFormData): Option[Subscription] = {
    for {
      verify <- formData.verify
      packaging <- formData.packaging
      packageCopackSmall = formData.packageCopackSmall.getOrElse(false)
      copacked <- formData.copacked
      imports <- formData.imports
      startDate <- formData.startDate
      productionSites = formData.productionSites.getOrElse(Nil)
      secondaryWarehouses = formData.secondaryWarehouses.getOrElse(Nil)
      contactDetails <- formData.contactDetails
    } yield {
      Subscription(
        utr = formData.utr,
        orgName = formData.rosmData.organisationName,
        orgType = toEnum(formData.orgType),
        address = UkAddress.fromAddress(formData.primaryAddress),
        activity = Activity(
          formData.packageOwn,
          formData.importVolume,
          formData.packageCopack,
          formData.packageCopackSmallVol,
          formData.copackedVolume
        ),
        liabilityDate = startDate,
        productionSites = productionSites.map(Site.fromAddress),
        warehouseSites = secondaryWarehouses.map(Site.fromAddress),
        contact = Contact(
          name = Some(contactDetails.fullName),
          positionInCompany = Some(contactDetails.position),
          phoneNumber = contactDetails.phoneNumber,
          email = contactDetails.email
        )
      )
    }
  }

  private def toEnum: Option[String] => Option[String] = {
    case Some("soleTrader") => Some("1")
    case Some("limitedLiabilityPartnership") => Some("2")
    case Some("partnership") => Some("3")
    case Some("unincorporatedBody") => Some("5")
    case Some("limitedCompany") => Some("7")
    case other => throw new IllegalArgumentException(s"Unexpected orgType $other")
  }
}