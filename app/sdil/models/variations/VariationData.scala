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
import play.api.mvc.Call
import sdil.controllers.variation.routes
import sdil.models.backend.Site
import sdil.models.retrieved.RetrievedSubscription
import sdil.models.{Address, ContactDetails, Litreage, Producer}

case class VariationData(original: RetrievedSubscription,
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
                         updatedWarehouseSites: Seq[Site], // TODO create variation Site model with trading name
                         updatedContactDetails: ContactDetails,
                         previousPages: Seq[Call]
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
  def isMaterialChange: Boolean = {
    val orig = VariationData(original)

    List(
      updatedContactDetails != orig.updatedContactDetails,
      isLiable != orig.isLiable,
      updatedWarehouseSites.nonEmpty,
      updatedProductionSites.nonEmpty
    ).foldLeft(false)(_ || _)
  }
}

object VariationData {
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

  implicit val format: Format[VariationData] = Json.format[VariationData]

  def apply(original: RetrievedSubscription): VariationData = VariationData(
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
    previousPages = List(routes.VariationsController.show)
  )
}
