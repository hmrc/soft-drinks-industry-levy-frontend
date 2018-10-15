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
import sdil.models.retrieved.RetrievedSubscription
import sdil.models.{Litreage, RegistrationFormData}

case class Subscription(utr: String,
                        orgName: String,
                        orgType: String,
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
      orgType <- formData.organisationType
      producer <- formData.producer
      startDate = formData.startDate.getOrElse(LocalDate.now)
      productionSites = formData.productionSites.getOrElse(Nil)
      secondaryWarehouses = formData.secondaryWarehouses.getOrElse(Nil)
      contactDetails <- formData.contactDetails
    } yield {
      Subscription(
        utr = formData.utr,
        orgName = formData.rosmData.organisationName,
        orgType = toEnum(orgType),
        address = UkAddress.fromAddress(formData.primaryAddress),
        activity = Activity(
          formData.volumeForOwnBrand,
          formData.importVolume,
          formData.volumeForCustomerBrands,
          formData.usesCopacker.collect { case true => Litreage(1, 1) },
          producer.isLarge.contains(true)
        ),
        liabilityDate = startDate,
        productionSites = productionSites,
        warehouseSites = secondaryWarehouses,
        contact = Contact(
          name = Some(contactDetails.fullName),
          positionInCompany = Some(contactDetails.position),
          phoneNumber = contactDetails.phoneNumber,
          email = contactDetails.email
        )
      )
    }
  }

  def desify(subscription: Subscription): Subscription = {
    subscription.copy(orgType = toEnum(subscription.orgType))
  }

  private def toEnum: String => String = {
    case "soleTrader" => "1"
    case "limitedLiabilityPartnership" => "2"
    case "partnership" => "3"
    case "unincorporatedBody" => "5"
    case "limitedCompany" => "7"
    case other => throw new IllegalArgumentException(s"Unexpected orgType $other")
  }
}
