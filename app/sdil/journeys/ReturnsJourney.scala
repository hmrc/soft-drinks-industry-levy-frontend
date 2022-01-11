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

import ltbs.uniform._
import validation._
import cats.implicits._
import ltbs.uniform.{ask, convertWithKey, end, interact, nonReturn, pure, tell}
import ltbs.uniform.validation.Rule
import play.api.i18n.Messages
import play.twirl.api.Html
import sdil.controllers.{askEmptyOption, askListSimple}
import sdil.models.{Address, ReturnPeriod, ReturnsVariation, SdilReturn, SmallProducer, Warehouse}
import sdil.models.backend.Site
import sdil.models.retrieved.RetrievedSubscription
import sdil.utility.stringToFormatter
import uk.gov.hmrc.http.HeaderCarrier
import views.html.uniform
import uk.gov.hmrc.domain._

import java.time.{LocalDate, LocalTime, ZoneId}
import java.time.format.DateTimeFormatter
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

object ReturnsJourney {

  def journey(
    sdilRef: String,
    period: ReturnPeriod,
    default: Option[SdilReturn] = None,
    subscription: RetrievedSubscription,
    broughtForward: BigDecimal,
    isSmallProd: Boolean,
    submitReturn: SdilReturn => Future[Unit],
    checkSmallProducerStatus: (String, ReturnPeriod) => Future[Option[Boolean]]
  ) = {

    val costLower = BigDecimal("0.18")
    val costHigher = BigDecimal("0.24")

    def calculateSubtotal(d: List[(String, (Long, Long), Int)]): BigDecimal =
      d.map { case (_, (l, h), m) => costLower * l * m + costHigher * h * m }.sum

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
      data = returnAmount(sdilReturn, isSmallProd)
      subtotal = calculateSubtotal(data)
      total = subtotal - broughtForward
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
          )
      _ <- convertWithKey(s"return-submission")((submitReturn(sdilReturn))) // sdilConnector.returns_update(subscription.utr, period, sdilReturn))
      _ <- nonReturn("return-complete")
      _ <- end(
            "return-sent", { msg: Messages =>
              val now = LocalDate.now
              val returnDate = msg(
                "return-sent.returnsDoneMessage",
                period.start.format("MMMM"),
                period.end.format("MMMM"),
                period.start.getYear.toString,
                subscription.orgName,
                LocalTime.now(ZoneId.of("Europe/London")).format(DateTimeFormatter.ofPattern("h:mma")).toLowerCase,
                now.format("dd MMMM yyyy")
              )

              val formatTotal =
                if (total < 0)
                  f"-£${total.abs}%,.2f"
                else
                  f"£$total%,.2f"

              val prettyPeriod =
                msg(
                  s"period.check-your-answers",
                  period.start.format("MMMM"),
                  period.end.format("MMMM yyyy")
                )

              val getTotal =
                if (total <= 0)
                  msg("return-sent.subheading.nil-return")
                else {
                  msg(
                    "return-sent.subheading",
                    prettyPeriod,
                    subscription.orgName
                  )
                }

              val whatHappensNext = views.html.uniform.fragments
                .returnsPaymentsBlurb(
                  subscription = subscription,
                  paymentDate = period,
                  sdilRef = sdilRef,
                  total = total,
                  formattedTotal = formatTotal,
                  variation = variation,
                  lineItems = data,
                  costLower = costLower,
                  costHigher = costHigher,
                  subtotal = subtotal,
                  broughtForward = broughtForward
                )(msg)
                .some

              views.html.uniform
                .journeyEndNew("return-sent", now, Html(returnDate).some, whatHappensNext, Html(getTotal).some)(msg)
            }
          )
    } yield (sdilReturn, variation)
  }
}
