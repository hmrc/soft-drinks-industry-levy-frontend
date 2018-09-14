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

package sdil.controllers
import java.time.LocalDateTime
import scala.concurrent._

import cats.implicits._
import ltbs.play.scaffold.GdsComponents._
import ltbs.play.scaffold.SdilComponents._
import play.api.libs.json.Format
import sdil.connectors.SoftDrinksIndustryLevyConnector
import sdil.models._
import sdil.models.retrieved.RetrievedSubscription
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.uniform.webmonad._

trait ReturnJourney extends SdilWMController {

  implicit val ec: ExecutionContext

  def askReturn(
    subscription: RetrievedSubscription,
    sdilRef: String,
    sdilConnector: SoftDrinksIndustryLevyConnector,
    default: Option[SdilReturn] = None
  )(implicit hc: HeaderCarrier): WebMonad[SdilReturn] = for {
    ownBrands      <- askEmptyOption(
      litreagePair, "own-brands-packaged-at-own-sites", default.map{_.ownBrand}
    ) emptyUnless !subscription.activity.smallProducer
    contractPacked <- askEmptyOption(litreagePair, "packaged-as-a-contract-packer", default.map{_.packLarge})
    askSmallProd   <- ask(bool, "exemptions-for-small-producers", default.map{_.packSmall.nonEmpty})
    firstSmallProd <- ask(smallProducer(sdilRef, sdilConnector), "first-small-producer-details", default.flatMap{_.packSmall.headOption}) when askSmallProd
    smallProds     <- manyT("small-producer-details",
      {ask(smallProducer(sdilRef, sdilConnector), _)},
      min = 1,
      default = default.fold(firstSmallProd.toList)(_.packSmall),
      editSingleForm = Some((smallProducer(sdilRef, sdilConnector), smallProducerForm))
    ) emptyUnless askSmallProd
    imports        <- askEmptyOption(litreagePair, "brought-into-uk", default.map{_.importLarge})
    importsSmall   <- askEmptyOption(litreagePair, "brought-into-uk-from-small-producers", default.map{_.importSmall})
    exportCredits  <- askEmptyOption(litreagePair, "claim-credits-for-exports", default.map{_.export})
    wastage        <- askEmptyOption(litreagePair, "claim-credits-for-lost-damaged", default.map{_.wastage})
  } yield SdilReturn(ownBrands,contractPacked,smallProds,imports,importsSmall,exportCredits,wastage)

}
