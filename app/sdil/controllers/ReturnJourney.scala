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

  def askReturn(subscription: RetrievedSubscription, sdilRef: String, sdilConnector: SoftDrinksIndustryLevyConnector)(implicit hc: HeaderCarrier): WebMonad[SdilReturn] = for {
    ownBrands      <- askEmptyOption(litreagePair, "own-brands-packaged-at-own-sites") emptyUnless !subscription.activity.smallProducer // TODO this needs scoping to the period
    contractPacked <- askEmptyOption(litreagePair, "packaged-as-a-contract-packer")
    askSmallProd   <- ask(bool, "exemptions-for-small-producers")
    firstSmallProd <- ask(smallProducer(sdilRef, sdilConnector), "first-small-producer-details") when askSmallProd
    smallProds     <- manyT("small-producer-details",
      {ask(smallProducer(sdilRef, sdilConnector), _)},
      min = 1,
      default = firstSmallProd.fold(List.empty[SmallProducer])(x => List(x)),
      editSingleForm = Some((smallProducer(sdilRef, sdilConnector), smallProducerForm))
    ) when askSmallProd
    imports        <- askEmptyOption(litreagePair, "brought-into-uk")
    importsSmall   <- askEmptyOption(litreagePair, "brought-into-uk-from-small-producers")
    exportCredits  <- askEmptyOption(litreagePair, "claim-credits-for-exports")
    wastage        <- askEmptyOption(litreagePair, "claim-credits-for-lost-damaged")
    sdilReturn     =  SdilReturn(ownBrands,contractPacked,smallProds.getOrElse(Nil),imports,importsSmall,exportCredits,wastage)
  } yield sdilReturn


}
