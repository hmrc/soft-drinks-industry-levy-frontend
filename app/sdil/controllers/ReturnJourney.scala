/*
 * Copyright 2021 HM Revenue & Customs
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

package sdil.controllers

import scala.concurrent._
import cats.implicits._
import ltbs.play.scaffold.GdsComponents._
import ltbs.play.scaffold.SdilComponents._
import play.api.i18n.Messages
import play.api.libs.json.JsValue
import sdil.connectors.SoftDrinksIndustryLevyConnector
import sdil.models._
import sdil.models.retrieved.RetrievedSubscription
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.uniform.playutil.ExtraMessages
import uk.gov.hmrc.uniform.webmonad._

trait ReturnJourney extends SdilWMController {

  implicit val ec: ExecutionContext

  def askReturn(
    subscription: RetrievedSubscription,
    sdilRef: String,
    sdilConnector: SoftDrinksIndustryLevyConnector,
    period: ReturnPeriod,
    default: Option[SdilReturn] = None,
    id: Option[String] = None
  )(implicit hc: HeaderCarrier, showBackLink: ShowBackLink, extraMessages: ExtraMessages): WebMonad[SdilReturn] = {

    def smallProdsJ: WebMonad[List[SmallProducer]] =
      for {
        editMode <- read[Boolean]("_editSmallProducers").map { _.getOrElse(false) }
        opt <- ask(bool("exemptions-for-small-producers"), "exemptions-for-small-producers", default.map {
                _.packSmall.nonEmpty
              })
        smallProdsJs <- execute(
                         sdilConnector.shortLiveCache
                           .fetchAndGetEntry[Map[String, JsValue]](sdilRef, s"returns-${period.year}${period.quarter}")
                           .flatMap { x =>
                             x.getOrElse(Map.empty).get("small-producer-details_data")
                           })
        smallProds <- manyT(
                       "small-producer-details", {
                         ask(
                           smallProducer(
                             sdilRef,
                             sdilConnector,
                             period,
                             getSmallProdsFromJs(smallProdsJs),
                             id.getOrElse("")),
                           _)(implicitly, implicitly, implicitly, ShowBackLink(true))
                       },
                       min = 1,
                       default = default.fold(List.empty[SmallProducer]) { _.packSmall },
                       editSingleForm = Some(
                         (
                           smallProducer(
                             sdilRef,
                             sdilConnector,
                             period,
                             getSmallProdsFromJs(smallProdsJs),
                             id.getOrElse("")),
                           smallProducerForm)),
                       configOverride = _.copy(mode = if (editMode) SingleStep else (LeapAhead))
                     )(implicitly, implicitly, implicitly, ShowBackLink(true)) emptyUnless opt
        _ <- write[Boolean]("_editSmallProducers", false)
      } yield { smallProds }

    for {
      // this update sets the value of the add another small producer question to no in adjustments journey
      editMode <- read[Boolean]("_editSmallProducers").map { _.getOrElse(false) }
      _        <- update[String]("small-producer-details")(_.getOrElse("Done").some) when !editMode
      ownBrands <- askEmptyOption(
                    litreagePair,
                    "own-brands-packaged-at-own-sites",
                    default.map { _.ownBrand }
                  ) emptyUnless !subscription.activity.smallProducer
      em = ExtraMessages(
        messages = if (subscription.activity.isVoluntaryMandatory) {
          Map(
            "brought-into-uk-from-small-producers.lead" ->
              Messages("brought-into-uk-from-small-producers.lead.volMan"),
            "claim-credits-for-exports.lead" ->
              Messages("claim-credits-for-exports.lead.volMan")
          )
        } else {
          Map.empty
        }
      )
      contractPacked <- askEmptyOption(litreagePair, "packaged-as-a-contract-packer", default.map { _.packLarge })
      smallProds     <- smallProdsJ
      imports        <- askEmptyOption(litreagePair, "brought-into-uk", default.map { _.importLarge })
      importsSmall <- askEmptyOption(
                       litreagePair,
                       "brought-into-uk-from-small-producers",
                       default.map { _.importSmall })(implicitly, implicitly, implicitly, implicitly, em)
      exportCredits <- askEmptyOption(litreagePair, "claim-credits-for-exports", default.map { _.export })(
                        implicitly,
                        implicitly,
                        implicitly,
                        implicitly,
                        em)
      wastage <- askEmptyOption(litreagePair, "claim-credits-for-lost-damaged", default.map { _.wastage })
    } yield SdilReturn(ownBrands, contractPacked, smallProds, imports, importsSmall, exportCredits, wastage)
  }

  private def getSmallProdsFromJs(smallProdsJs: Option[JsValue]): List[SmallProducer] =
    smallProdsJs match {
      case Some(y: JsValue) => y.as[List[SmallProducer]]
      case None             => List()
    }

}
