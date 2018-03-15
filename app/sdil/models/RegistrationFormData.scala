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

package sdil.models

import java.time.LocalDate

import play.api.libs.json._
import play.api.libs.functional.syntax._

case class RegistrationFormData(rosmData: RosmRegistration,
                                utr: String,
                                verify: Option[DetailsCorrect] = None,
                                organisationType: Option[String] = None,
                                producer: Option[Producer] = None,
                                packaging: Option[Packaging] = None,
                                volumeForOwnBrand: Option[Litreage] = None,
                                packagesForOthers: Option[Boolean] = None,
                                volumeForCustomerBrands: Option[Litreage] = None,
                                packagesForSmallProducers: Option[Boolean] = None,
                                volumeForSmallProducers: Option[Litreage] = None,
                                usesCopacker: Option[Boolean] = None,
                                volumeByCopackers: Option[Litreage] = None,
                                isImporter: Option[Boolean] = None,
                                importVolume: Option[Litreage] = None,
                                confirmedSmallProducer: Option[Boolean] = None,
                                startDate: Option[LocalDate] = None,
                                productionSites: Option[Seq[Address]] = None,
                                secondaryWarehouses: Option[Seq[Address]] = None,
                                contactDetails: Option[ContactDetails] = None) {

  lazy val isNotMandatory: Boolean = {
    total(volumeForOwnBrand, volumeByCopackers) < 1000000 &&
      volumeByCopackers.forall(_.total == 0) &&
      !packaging.exists(_.packagesCustomerBrands) &&
      !isImporter.getOrElse(false)
  }

  lazy val isSmall: Boolean = {
    val q = total(volumeForOwnBrand, volumeByCopackers)
    q < 1000000 && q > 0
  }

  lazy val isCopacked: Boolean = {
    volumeByCopackers.exists(_.total != 0)
  }

  lazy val isVoluntary: Boolean = {
    isSmall && isCopacked && isImporter.contains(false) && volumeForCustomerBrands.isEmpty
  }

  def total(n: Option[Litreage]*): BigDecimal = {
    (n map { x => x.fold[BigDecimal](0)(_.total) }).sum
  }

  lazy val primaryAddress: Address = {
    verify match {
      case Some(DetailsCorrect.DifferentAddress(a)) => a
      case _ => rosmData.address
    }
  }
}

object RegistrationFormData {
  // to be able to read existing save4later session data
  implicit val format: Format[RegistrationFormData] = (
    (__ \ "rosmData").format[RosmRegistration] and
      (__ \ "utr").format[String] and
      (__ \ "verify").formatNullable[DetailsCorrect] and
      (__ \ "orgType").formatNullable[String] and
      (__ \ "producer").formatNullable[Producer] and
      (__ \ "packaging").formatNullable[Packaging] and
      (__ \ "packageOwn").formatNullable[Litreage] and
      (__ \ "packageCopack").formatNullable[Boolean] and
      (__ \ "packageCopackVol").formatNullable[Litreage] and
      (__ \ "packageCopackSmall").formatNullable[Boolean] and
      (__ \ "packageCopackSmallVol").formatNullable[Litreage] and
      (__ \ "copacked").formatNullable[Boolean] and
      (__ \ "copackedVolume").formatNullable[Litreage] and
      (__ \ "imports").formatNullable[Boolean] and
      (__ \ "importVolume").formatNullable[Litreage] and
      (__ \ "smallProducerConfirmFlag").formatNullable[Boolean] and
      (__ \ "startDate").formatNullable[LocalDate] and
      (__ \ "productionSites").formatNullable[Seq[Address]] and
      (__ \ "secondaryWarehouses").formatNullable[Seq[Address]] and
      (__ \ "contactDetails").formatNullable[ContactDetails]
    ) (RegistrationFormData.apply, unlift(RegistrationFormData.unapply))
}
