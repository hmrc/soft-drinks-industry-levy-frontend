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

import cats.implicits._

object Convert {

  implicit class RichA[A](first: A) {

    /** if the first value is the same as the second then
      * return None - otherwise return Some(first)
      */
    def ifDifferentTo(other: A): Option[A] =
      if (first == other) None else Some(first)
  }

  private def nonEmptyString(i: String) = i.some.filter(_.nonEmpty)

  def apply(vd: VariationData, todaysDate: LocalDate = LocalDate.now()): VariationsRequest = {
    val orig = vd.original

    val newBusinessContact = {
      val address = vd.updatedBusinessDetails.address
      VariationsContact(
        nonEmptyString(address.line1),
        nonEmptyString(address.line2),
        nonEmptyString(address.line3),
        nonEmptyString(address.line4),
        address.postcode.some,
        nonEmptyString(vd.updatedContactDetails.phoneNumber),
        nonEmptyString(vd.updatedContactDetails.email)
      )
    }
    val oldBusinessContact = {
      val lines = vd.original.address.lines.toVector
      VariationsContact(
        lines.headOption,
        lines.get(1),
        lines.get(2),
        lines.get(3),
        vd.original.address.postCode.some,
        nonEmptyString(vd.original.contact.phoneNumber),
        nonEmptyString(vd.original.contact.email)
      )
    }

    val newPersonalDetails = {
      val contact = vd.updatedContactDetails
      VariationsPersonalDetails(
        nonEmptyString(contact.fullName),
        nonEmptyString(contact.position),
        nonEmptyString(contact.phoneNumber),
        nonEmptyString(contact.email)
      )
    }
    val oldPersonalDetails = {
      val contact = vd.original.contact
      VariationsPersonalDetails(
        contact.name,
        contact.positionInCompany,
        nonEmptyString(contact.phoneNumber),
        nonEmptyString(contact.email)
      )
    }

    val newSdilActivity = {
      SdilActivity(
        backendActivity(???),
        vd.producer.isLarge, /* opposite of large */
        vd.producer.isLarge, /* opposite of large */
        vd.usesCopacker,
        voluntarilyRegistered = ???,
        reasonForAmendment = ???,
        estimatedTaxAmount = ???, /* calculation of all the litres in VariationData */
        taxObligationStartDate = ??? /* not part of VariationData */
      )
    }
    val oldSdilActivity = {
      val retrievedActivity = vd.original.activity
      SdilActivity(
        backendActivity(???),
        retrievedActivity.smallProducer.some,
        retrievedActivity.smallProducer.some,
        retrievedActivity.contractPacker.some,
        retrievedActivity.voluntaryRegistration.some,
        reasonForAmendment = None, /* you wouldn't be able to retrieve that surely? */
        estimatedTaxAmount = None, /* we get no litres back from ETMP */
        taxObligationStartDate = if (???) Some(todaysDate) else None /* do we get that back from ETMP? */
      )
    }

    val newSites = {}
    val amendSites = {}
    val closeSites = {}
    val oldSites = {}

    VariationsRequest(
      tradingName = vd.updatedBusinessDetails.tradingName ifDifferentTo orig.orgName,
      businessContact = newBusinessContact ifDifferentTo oldBusinessContact,
      correspondenceContact = newBusinessContact ifDifferentTo oldBusinessContact, // to confirm
      primaryPersonContact = newPersonalDetails ifDifferentTo oldPersonalDetails,
      sdilActivity = newSdilActivity ifDifferentTo oldSdilActivity,
      deregistrationText = ???,
      newSites = List[VariationsSite](???),
      amendSites = List[VariationsSite](???),
      closeSites = List[CloseSites](???)
    )
  }
}
