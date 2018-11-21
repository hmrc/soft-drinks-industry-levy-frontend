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
import sdil.models.backend.Site

case class RegistrationFormData(rosmData: RosmRegistration,
                                utr: String,
                                verify: Option[DetailsCorrect] = None,
                                organisationType: Option[String] = None,
                                producer: Option[Producer] = None,
                                isPackagingForSelf: Option[Boolean] = None,
                                volumeForOwnBrand: Option[Litreage] = None,
                                packagesForOthers: Option[Boolean] = None,
                                volumeForCustomerBrands: Option[Litreage] = None,
                                usesCopacker: Option[Boolean] = None,
                                isImporter: Option[Boolean] = None,
                                importVolume: Option[Litreage] = None,
                                startDate: Option[LocalDate] = None,
                                productionSites: Option[Seq[Site]] = None,
                                secondaryWarehouses: Option[Seq[Site]] = None,
                                contactDetails: Option[ContactDetails] = None) {

  /**
    * users cannot register if they are a small producer globally, do not use a copacker, and do not copack or import in the UK
    */
  lazy val isNotAllowedToRegister: Boolean = {
    isSmallProducerWithNoCopacker && doesNotCopackOrImport
  }

  lazy val doesNotCopackOrImport: Boolean = packagesForOthers.contains(false) && isImporter.contains(false)

  lazy val isSmallProducerWithNoCopacker: Boolean = {
    producer.flatMap(_.isLarge).forall(_ == false) && usesCopacker.forall(_ == false)
  }

  lazy val isVoluntary: Boolean = {
    producer.flatMap(_.isLarge).forall(_ == false) &&
      usesCopacker.contains(true) &&
      isImporter.contains(false) &&
      packagesForOthers.contains(false)
  }
  
  lazy val primaryAddress: Address = {
    verify match {
      case Some(DetailsCorrect.DifferentAddress(a)) => a
      case _ => rosmData.address
    }
  }

  lazy val hasPackagingSites: Boolean =
    Seq(producer.flatMap(_.isLarge), isPackagingForSelf).forall(_.contains(true)) ||
      packagesForOthers.contains(true)
}

object RegistrationFormData {
  implicit val format: Format[RegistrationFormData] = Json.format[RegistrationFormData]
}
