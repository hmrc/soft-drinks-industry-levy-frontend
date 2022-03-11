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
import views.uniform.fragments.returnsCYACantClaim

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

object ReturnsJourney {

  val costLower = BigDecimal("0.18")
  val costHigher = BigDecimal("0.24")

  def taxEstimation(r: SdilReturn): BigDecimal = {
    val t = r.packLarge |+| r.importLarge |+| r.ownBrand
    (t._1 * costLower |+| t._2 * costHigher) * 4
  }
  def returnAmount(sdilReturn: SdilReturn, isSmallProducer: Boolean): List[(String, (Long, Long), Int)] = {
    val ra = List(
      ("packaged-as-a-contract-packer", sdilReturn.packLarge, 1),
      ("exemptions-for-small-producers", sdilReturn.packSmall.map { _.litreage }.combineAll, 0),
      ("brought-into-uk", sdilReturn.importLarge, 1),
      ("brought-into-uk-from-small-producers", sdilReturn.importSmall, 0),
      ("claim-credits-for-exports", sdilReturn.export, -1),
      ("claim-credits-for-lost-damaged", sdilReturn.wastage, -1)
    )
    if (!isSmallProducer)
      ("own-brands-packaged-at-own-sites", sdilReturn.ownBrand, 1) :: ra
    else
      ra
  }
  def calculateSubtotal(d: List[(String, (Long, Long), Int)]): BigDecimal =
    d.map { case (_, (l, h), m) => costLower * l * m + costHigher * h * m }.sum

  def message(key: String, args: String*) = {
    import play.twirl.api.HtmlFormat.escape
    Map(key -> Tuple2(key, args.toList.map { escape(_).toString }))
  }

  val overClaimHtml = uniform.fragments.overClaim("overclaim-warning", "suggest-overClaim")(_: Messages)
  val defaultTotalReturn = BigDecimal(0.0)

  val NoClaim: (Long, Long) = (0L, 0L)
  def journey(
    period: ReturnPeriod,
    default: Option[SdilReturn] = None,
    subscription: RetrievedSubscription,
    checkSmallProducerStatus: (String, ReturnPeriod) => Future[Option[Boolean]],
    submitReturnVariation: ReturnsVariation => Future[Unit],
    broughtForward: BigDecimal,
    isSmallProd: Boolean,
    totalReturn: Future[BigDecimal] = Future(defaultTotalReturn),
    canClaim: Boolean = false
  ) =
    for {
      ownBrands <- askEmptyOption[(Long, Long)](
                    "own-brands-packaged-at-own-sites",
                    default = default.map { _.ownBrand }
                  ) emptyUnless !subscription.activity.smallProducer
      contractPacked <- askEmptyOption[(Long, Long)](
                         "packaged-as-a-contract-packer",
                         default = default.map { _.packLarge }
                       )
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
      exportCredits <- askEmptyOption[(Long, Long)]("claim-credits-for-exports", default.map { _.export }) when canClaim == true

      exports = (exportCredits.foldLeft(0L)(_ + _._2) * costHigher) + (exportCredits.foldLeft(0L)(_ + _._1) * costLower)

      _ <- tell("overclaim-warning", overClaimHtml) when (exports) + Await.result(totalReturn, 20.seconds).toLong > 0

      wastage <- askEmptyOption[(Long, Long)]("claim-credits-for-lost-damaged", default.map { _.wastage }) when canClaim == true

      wastages = (wastage.foldLeft(0L)(_ + _._2) * costHigher) + (wastage.foldLeft(0L)(_ + _._1) * costLower) + (exportCredits
        .foldLeft(0L)(_ + _._2) * costHigher) + (exportCredits
        .foldLeft(0L)(_ + _._1) * costLower)

      _ <- tell("overclaim-warning2", overClaimHtml) when (wastages) + Await.result(totalReturn, 20.seconds).toLong > 0

      sdilReturn = SdilReturn(
        ownBrands,
        contractPacked,
        smallProds,
        imports,
        importsSmall,
        exportCredits.getOrElse(NoClaim),
        wastage.getOrElse(NoClaim))
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
      data = returnAmount(sdilReturn, isSmallProd)
      subtotal = calculateSubtotal(data)
      total = subtotal - broughtForward
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
      _ <- convertWithKey("vary-in-return")(submitReturnVariation(variation))

      _ <- tell(
            "check-your-answers",
            uniform.fragments.returnsCYA(
              key = "check-your-answers",
              lineItems = data,
              costLower = costLower,
              costHigher = costHigher,
              subtotal = subtotal,
              broughtForward = broughtForward,
              total = total,
              variation = variation.some,
              subscription = subscription,
              originalReturn = None
            )(_: Messages)
            //TODO: Custom Content doesn't appear to be working
//        , customContent = message("heading.check-your-answers.orgName", subscription.orgName)
          ) when canClaim == true
      _ <- tell(
            "check-your-answers",
            returnsCYACantClaim(
              key = "check-your-answers",
              lineItems = data,
              costLower = costLower,
              costHigher = costHigher,
              subtotal = subtotal,
              broughtForward = broughtForward,
              total = total,
              variation = variation.some,
              subscription = subscription,
              originalReturn = None
            )(_: Messages)
            //TODO: Custom Content doesn't appear to be working
            //        , customContent = message("heading.check-your-answers.orgName", subscription.orgName)
          ) when canClaim == false
    } yield (sdilReturn, variation)
}
