/*
 * Copyright 2019 HM Revenue & Customs
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
import play.api.mvc.Call
import sdil.models.backend.{Site, UkAddress}
import sdil.models.retrieved.RetrievedSubscription
import sdil.models.{Address, ContactDetails, Litreage, Producer, ReturnPeriod, SdilReturn, SmallProducer}

case class ReturnVariationData(
  original: SdilReturn,
  revised: SdilReturn,
  period: ReturnPeriod,
  orgName: String,
  address: UkAddress,
  reason: String,
  repaymentMethod: Option[String] = None
) {

  def changedLitreages: Map[String, ((Long, Long),(Long,Long))] = original.compare(revised)
  def removedSmallProducers: List[SmallProducer] = original.packSmall.filterNot(revised.packSmall.toSet)
  def addedSmallProducers: List[SmallProducer] = revised.packSmall.filterNot(original.packSmall.toSet)

}

case class RegistrationVariationData(
  original: RetrievedSubscription,
  updatedBusinessAddress: Address,
  producer: Producer,
  usesCopacker: Option[Boolean],
  packageOwn: Option[Boolean],
  packageOwnVol: Option[Litreage],
  copackForOthers: Boolean,
  copackForOthersVol: Option[Litreage],
  imports: Boolean,
  importsVol: Option[Litreage],
  updatedProductionSites: Seq[Site],
  updatedWarehouseSites: Seq[Site],
  updatedContactDetails: ContactDetails,
  previousPages: Seq[Call],
  reason: Option[String] = None,
  deregDate: Option[LocalDate] = None
) {

  def isLiablePacker: Boolean = {
    producer.isLarge.getOrElse(false) || copackForOthers
  }

  def isLiable: Boolean = {
    (producer.isProducer && producer.isLarge.getOrElse(false)) || imports || copackForOthers
  }

  def isVoluntary: Boolean = {
    usesCopacker.getOrElse(false) && producer.isLarge.contains(false) && !isLiable
  }

  /** Material changes are updates to sites, reporting liability or
    * contact details.
    *
    * A material change must by law be reported as a variation
    * whereas a non-material change cannot be submitted as a variation.
    */

  lazy val orig = RegistrationVariationData(original)

  lazy val volToMan: Boolean = orig.isVoluntary && isLiable

  lazy val manToVol: Boolean = orig.isLiable && isVoluntary

  def isMaterialChange: Boolean = {
    List(
      updatedContactDetails != orig.updatedContactDetails,
      isLiable != orig.isLiable,
      updatedWarehouseSites.nonEmpty,
      updatedProductionSites.nonEmpty,
      deregDate.isDefined
    ).foldLeft(false)(_ || _)
  }
}


object RegistrationVariationData {
  import sdil.connectors._

  implicit val callWrites: Format[Call] = new Format[Call] {
    override def writes(o: Call): JsValue = {
      Json.obj(
        "method" -> o.method,
        "url" -> o.url
      )
    }

    override def reads(json: JsValue): JsResult[Call] = for {
      method <- (json \ "method").validate[String]
      url <- (json \ "url").validate[String]
    } yield Call(method, url)
  }

  implicit val returnTupleFormat: Format[(SdilReturn,SdilReturn)] = Json.format[(SdilReturn,SdilReturn)]
  implicit val format: Format[RegistrationVariationData] = Json.format[RegistrationVariationData]


  def apply(original: RetrievedSubscription): RegistrationVariationData = RegistrationVariationData(
    original,
    Address.fromUkAddress(original.address),
    Producer(original.activity.largeProducer || original.activity.smallProducer, Some(original.activity.largeProducer)),
    usesCopacker = if(original.activity.voluntaryRegistration) Some(true) else None,
    packageOwn = None,
    packageOwnVol = None,
    original.activity.contractPacker,
    copackForOthersVol = None,
    original.activity.importer,
    importsVol = None,
    original.productionSites.filter(_.closureDate.forall(_.isAfter(LocalDate.now))),
    original.warehouseSites.filter(_.closureDate.forall(_.isAfter(LocalDate.now))),
    ContactDetails(
      original.contact.name.getOrElse(""),
      original.contact.positionInCompany.getOrElse(""),
      original.contact.phoneNumber,
      original.contact.email),
    previousPages = Nil
  )
}
