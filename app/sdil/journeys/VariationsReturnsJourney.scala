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
import play.api.Logger
import play.api.i18n.Messages
import play.twirl.api.Html
import sdil.config.AppConfig
import sdil.connectors.SoftDrinksIndustryLevyConnector
import sdil.controllers.{askEmptyOption, askListSimple}
import sdil.journeys.ReturnsJourney.{calculateSubtotal, returnAmount}
import sdil.journeys.VariationsJourney.RepaymentMethod
import sdil.models.backend.Site
import sdil.models.retrieved.RetrievedSubscription
import sdil.models.variations.{RegistrationVariationData, ReturnVariationData}
import sdil.models.{Address, ReturnPeriod, ReturnsVariation, SdilReturn, SmallProducer, Warehouse, extractTotal, listItemsWithTotal}
import uk.gov.hmrc.http.HeaderCarrier
import views.html.uniform

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}

object VariationsReturnsJourney {

  val logger: Logger = Logger(this.getClass())

  val costLower = BigDecimal("0.18")
  val costHigher = BigDecimal("0.24")

  def taxEstimation(r: SdilReturn): BigDecimal = {
    val t = r.packLarge |+| r.importLarge |+| r.ownBrand
    (t._1 * costLower |+| t._2 * costHigher) * 4
  }

  def journey(
    id: String,
    subscription: RetrievedSubscription,
    sdilRef: String,
    default: Option[SdilReturn] = None,
    variableReturns: List[ReturnPeriod],
    broughtForward: BigDecimal,
    checkSmallProducerStatus: (String, ReturnPeriod) => Future[Option[Boolean]],
    getReturn: ReturnPeriod => Future[Option[SdilReturn]],
    config: AppConfig,
  ) =
    for {
      period <- ask[ReturnPeriod](
                 "select-return",
                 validation = Rule.in(variableReturns)
               )

      isSmallProd <- convertWithKey("is-small-producer")(checkSmallProducerStatus(sdilRef, period))

      origReturn <- convertWithKey("return-lookup")(getReturn(period))

      ownBrands <- askEmptyOption[(Long, Long)](
                    "own-brands-packaged-at-own-sites",
                    default = origReturn.map {
                      _.ownBrand
                    }
                  ) emptyUnless !subscription.activity.smallProducer

      contractPacked <- askEmptyOption[(Long, Long)](
                         "packaged-as-a-contract-packer",
                         default = origReturn.map {
                           _.packLarge
                         }
                       )

      smallProds <- askList[SmallProducer]("small-producer-details", origReturn.map {
                     _.packSmall
                   }, Rule.nonEmpty[List[SmallProducer]])(
                     {
                       case (index: Option[Int], existingSmallProducers: List[SmallProducer]) =>
                         ask[SmallProducer](
                           s"add-small-producer",
                           default = index.map(existingSmallProducers),
                           validation = Rule.condAtPath[SmallProducer]("sdilRef")(
                             sp =>
                               Await
                                 .result(checkSmallProducerStatus(sp.sdilRef, period), 20.seconds)
                                 .getOrElse(true),
                             "notSmall"
                           ) followedBy Rule.condAtPath[SmallProducer]("sdilRef")(
                             sp => !(sp.sdilRef === subscription.sdilRef),
                             "same"
                           ) followedBy Rule.condAtPath[SmallProducer]("sdilRef")(
                             sp =>
                               !(id
                                 .contains("/add/") && existingSmallProducers
                                 .map(s => s.sdilRef)
                                 .contains(sp.sdilRef)),
                             "alreadyexists"
                           )
                         )
                     }, {
                       case (index: Int, existingSmallProducers: List[SmallProducer]) =>
                         interact[Boolean]("remove-small-producer-details", existingSmallProducers(index).sdilRef)
                     }
                   ) emptyUnless ask[Boolean]("exemptions-for-small-producers", default = origReturn.map {
                     _.packSmall.nonEmpty
                   })

      imports <- askEmptyOption[(Long, Long)]("brought-into-uk", origReturn.map {
                  _.importLarge
                })

      importsSmall <- askEmptyOption[(Long, Long)]("brought-into-uk-from-small-producers", origReturn.map {
                       _.importSmall
                     })

      exportCredits <- askEmptyOption[(Long, Long)]("claim-credits-for-exports", origReturn.map {
                        _.export
                      })

      wastage <- askEmptyOption[(Long, Long)]("claim-credits-for-lost-damaged", origReturn.map {
                  _.wastage
                })

      sdilReturn = SdilReturn(ownBrands, contractPacked, smallProds, imports, importsSmall, exportCredits, wastage)

      isNewImporter = (sdilReturn.totalImported._1 > 0L && sdilReturn.totalImported._2 > 0L) && !subscription.activity.importer

      isNewPacker = (sdilReturn.totalPacked._1 > 0L && sdilReturn.totalPacked._2 > 0L) && !subscription.activity.contractPacker

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
                      } yield warehouses) when isNewImporter && subscription.warehouseSites.isEmpty

      data = returnAmount(sdilReturn, isSmallProd.getOrElse(true))
      subtotal = calculateSubtotal(data)
      total = subtotal - broughtForward
      thisVariation = ReturnsVariation(
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
              variation = thisVariation.some,
              subscription = subscription,
              period = period,
              originalReturn = None
            )(_: Messages)
          )

      emptyReturn = SdilReturn((0, 0), (0, 0), Nil, (0, 0), (0, 0), (0, 0), (0, 0), None)
      variation = ReturnVariationData(
        origReturn.getOrElse(emptyReturn),
        sdilReturn,
        period,
        RegistrationVariationData(subscription).original.orgName,
        RegistrationVariationData(subscription).original.address,
        ""
      )

      reason <- ask[String](
                 "return-correction-reason",
                 validation = Rule.nonEmpty[String]("required") followedBy Rule.maxLength(255))

      repayment <- ask[RepaymentMethod]("repayment-method") when
                    (variation.revised.total - variation.original.total < 0)

      repaymentString = repayment match {
        case Some(RepaymentMethod.Credit)      => "credit".some
        case Some(RepaymentMethod.BankPayment) => "bankPayment".some
        case None                              => None
      }

      payMethodAndReason = variation.copy(reason = reason, repaymentMethod = repaymentString)

      _ <- tell(
            "check-return-changes",
            uniform.fragments.returnVariationDifferences(
              "check-return-changes",
              payMethodAndReason,
              showChangeLinks = true,
              broughtForward.some
            )(_: Messages)
          )

    } yield (payMethodAndReason)
}
