/*
 * Copyright 2022 HM Revenue & Customs
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

package sdil.journeys

import cats.implicits._
import ltbs.uniform.validation.{Rule, _}
import ltbs.uniform._
import play.api.i18n.Messages
import sdil.controllers.{askEmptyOption, askListSimple}
import sdil.models.backend.Site
import sdil.models.retrieved.RetrievedSubscription
import sdil.models.{Address, ReturnPeriod, ReturnsVariation, SdilReturn, SmallProducer, Warehouse}
import views.html.uniform

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

object ReturnsJourney {

  def journey(
    period: ReturnPeriod,
    default: Option[SdilReturn] = None,
    subscription: RetrievedSubscription,
    checkSmallProducerStatus: (String, ReturnPeriod) => Future[Option[Boolean]]
  ) = {

    val costLower = BigDecimal("0.18")
    val costHigher = BigDecimal("0.24")

    def taxEstimation(r: SdilReturn): BigDecimal = {
      val t = r.packLarge |+| r.importLarge |+| r.ownBrand
      (t._1 * costLower |+| t._2 * costHigher) * 4
    }

    for {
      ownBrands <- askEmptyOption[(Long, Long)](
                    "own-brands-packaged-at-own-sites",
                    default = default.map { _.ownBrand }
                  ) emptyUnless !subscription.activity.smallProducer
      contractPacked <- askEmptyOption[(Long, Long)](
                         "packaged-as-a-contract-packer",
                         default = default.map { _.packLarge }
                       ) emptyUnless !subscription.activity.smallProducer
      smallProds <- askList[SmallProducer](
                     "small-producer-details",
                     default.map { _.packSmall },
                     Rule.nonEmpty[List[SmallProducer]]) {
                     case (index: Option[Int], existing: List[SmallProducer]) =>
                       ask[SmallProducer](
                         s"add-small-producer",
                         default = index.map(existing),
                         validation = Rule.condAtPath("sdilRef")(
                           sp => Await.result(checkSmallProducerStatus(sp.sdilRef, period), 20.seconds).getOrElse(true),
                           "notSmall"
                         )
//                     TODO: Check with Luke why followedBy doesn't work here
//                           Rule.condAtPath("sdilRef")(
//                           sp => existing.map(
//                             existingSmallProds => sp.sdilRef =!= existingSmallProds.sdilRef
//                           ).headOption.getOrElse(true),
//                           "alreadyexists"
//                         ) followedBy Rule.condAtPath("sdilRef")(
//                           sp => !(sp.sdilRef === subscription.sdilRef),
//                           "error.sdilref.same"
//                         )
                       )
                   } emptyUnless ask[Boolean]("exemptions-for-small-producers", default = default.map {
                     _.packSmall.nonEmpty
                   })
      imports <- askEmptyOption[(Long, Long)]("brought-into-uk", default.map { _.importLarge })
      importsSmall <- askEmptyOption[(Long, Long)]("brought-into-uk-from-small-producers", default.map {
                       _.importSmall
                     })
      exportCredits <- askEmptyOption[(Long, Long)]("claim-credits-for-exports", default.map { _.export })
      wastage       <- askEmptyOption[(Long, Long)]("claim-credits-for-lost-damaged", default.map { _.wastage })
      sdilReturn = SdilReturn(ownBrands, contractPacked, smallProds, imports, importsSmall, exportCredits, wastage)
      isNewImporter = !sdilReturn.totalImported.isEmpty && !subscription.activity.importer
      isNewPacker = !sdilReturn.totalPacked.isEmpty && !subscription.activity.contractPacker
      inner = uniform.fragments.return_variation_continue(isNewImporter, isNewPacker)(_: Messages)
      _ <- tell("return-change-registration", inner) when isNewImporter || isNewPacker
      newPackingSites <- (
                          for {
                            firstPackingSite <- interact[Boolean](
                                                 "pack-at-business-address-in-return",
                                                 Address.fromUkAddress(subscription.address)) flatMap {
                                                 case true =>
                                                   pure(Address.fromUkAddress(subscription.address))
                                                 case false => ask[Address]("first-production-site")
                                               }
                            packingSites <- askListSimple[Address](
                                             "production-site-details",
                                             "site-in-return",
                                             default = Some(firstPackingSite :: Nil),
                                             listValidation = Rule.nonEmpty[List[Address]]
                                           ).map(_.map(Site.fromAddress))
                          } yield packingSites
                        ) when isNewPacker && subscription.productionSites.isEmpty
      newWarehouses <- (for {
                        addWarehouses <- ask[Boolean]("ask-secondary-warehouses-in-return")
                        warehouses <- askListSimple[Warehouse](
                                       "secondary-warehouse-details",
                                       "warehouse-in-return",
                                       listValidation = Rule.nonEmpty[List[Warehouse]]
                                     ) map (_.map(Site.fromWarehouse)) emptyUnless addWarehouses
                      } yield warehouses) when isNewImporter //&& subscription.warehouseSites.isEmpty
      variation = ReturnsVariation(
        orgName = subscription.orgName,
        ppobAddress = subscription.address,
        importer = (isNewImporter, (sdilReturn.totalImported).combineN(4)),
        packer = (isNewPacker, (sdilReturn.totalPacked).combineN(4)),
        warehouses = newWarehouses.getOrElse(List.empty[Site]),
        packingSites = newPackingSites.getOrElse(List.empty[Site]),
        phoneNumber = subscription.contact.phoneNumber,
        email = subscription.contact.email,
        taxEstimation = taxEstimation(sdilReturn)
      )
    } yield (sdilReturn, variation)
  }
}
