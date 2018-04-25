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
import sdil.models.Litreage
import sdil.models.backend.{Activity, UkAddress}

object Convert {

  implicit class RichA[A](first: A) {

    /** if the first value is the same as the second then
      * return None - otherwise return Some(first)
      */
    def ifDifferentTo(other: A): Option[A] =
      if (first == other) None else Some(first)
  }

  implicit class PoorA[A <: {def nonEmpty: Boolean}](a: A) {
    def ifNonEmpty: Option[A] = if (a.nonEmpty) Some(a) else None
  }

  def apply(vd: VariationData, todaysDate: LocalDate = LocalDate.now()): VariationsSubmission = {
    val orig = vd.original

    val newBusinessContact = {
      val address = vd.updatedBusinessDetails.address
      val original = vd.original.address

      VariationsContact(
        if (address.nonEmptyLines != original.lines || address.postcode != original.postCode) {
          Some(address)
        } else {
          None
        },
        vd.updatedContactDetails.phoneNumber ifDifferentTo orig.contact.phoneNumber,
        vd.updatedContactDetails.email ifDifferentTo orig.contact.email
      )
    }

    val newPersonalDetails = {
      val contact = vd.updatedContactDetails
      val original = orig.contact

      VariationsPersonalDetails(
        contact.fullName ifDifferentTo original.name.getOrElse(""),
        contact.position ifDifferentTo original.positionInCompany.getOrElse(""),
        contact.phoneNumber ifDifferentTo original.phoneNumber,
        contact.email ifDifferentTo original.email
      )
    }

    val newSdilActivity = {
      val activity = Activity(
        if (vd.packageOwn.contains(true) && vd.producer.isProducer) vd.packageOwnVol else None,
        if (vd.imports) vd.importsVol else None,
        if (vd.copackForOthers) vd.copackForOthersVol else None,
        vd.usesCopacker.collect { case true => Litreage(1, 1) },
        vd.producer.isLarge.contains(true)
      )

      SdilActivity(
        activity = if (activity.nonEmpty || activity.isLarge != orig.activity.largeProducer) Some(activity) else None,
        produceLessThanOneMillionLitres = !activity.isLarge ifDifferentTo !orig.activity.largeProducer,
        smallProducerExemption = vd.isVoluntary ifDifferentTo orig.activity.voluntaryRegistration,
        usesContractPacker = vd.isVoluntary ifDifferentTo orig.activity.voluntaryRegistration,
        voluntarilyRegistered = vd.isVoluntary ifDifferentTo orig.activity.voluntaryRegistration,
        reasonForAmendment = None,
        taxObligationStartDate = if (orig.activity.voluntaryRegistration && vd.isLiable) Some(todaysDate) else None
      )
    }

    val newSites = {}
    val amendSites = {}
    val closeSites = {}
    val oldSites = {}

    VariationsSubmission(
      tradingName = vd.updatedBusinessDetails.tradingName ifDifferentTo orig.orgName,
      businessContact = newBusinessContact.ifNonEmpty,
      correspondenceContact = newBusinessContact.ifNonEmpty,
      primaryPersonContact = newPersonalDetails.ifNonEmpty,
      sdilActivity = newSdilActivity.ifNonEmpty,
      deregistrationText = None,
      newSites = Nil,
      amendSites = Nil,
      closeSites = Nil
    )
  }
}
