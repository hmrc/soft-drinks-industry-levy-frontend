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
  )(implicit hc: HeaderCarrier, showBackLink: Boolean = true): WebMonad[SdilReturn] = {

    def smallProdsJ: WebMonad[List[SmallProducer]] = for {
      editMode        <- read[Boolean]("_editSmallProducers").map{_.getOrElse(false)}
      opt             <- ask(bool, "exemptions-for-small-producers", default.map{_.packSmall.nonEmpty})

      smallProds      <- manyT("small-producer-details",
                               {ask(smallProducer(sdilRef, sdilConnector), _)(implicitly,implicitly,implicitly,true)},
                               min = 1,
                               default = default.fold(List.empty[SmallProducer]){_.packSmall},
                               editSingleForm = Some((smallProducer(sdilRef, sdilConnector), smallProducerForm)),
                               configOverride = _.copy(mode = if(editMode) SingleStep else (LeapAhead))
                              )(implicitly, implicitly, true) emptyUnless opt
      _               <- write[Boolean]("_editSmallProducers", false)
    } yield { smallProds }

    for {
      // this update sets the value of the add another small producer question to no
      _ <- update[String]("small-producer-details")(_.getOrElse("Done").some)
      ownBrands      <- askEmptyOption(
        litreagePair, "own-brands-packaged-at-own-sites", default.map{_.ownBrand}
      ) emptyUnless !subscription.activity.smallProducer
      contractPacked <- askEmptyOption(litreagePair, "packaged-as-a-contract-packer", default.map{_.packLarge})
      smallProds     <- smallProdsJ
      imports        <- askEmptyOption(litreagePair, "brought-into-uk", default.map{_.importLarge})
      importsSmall   <- askEmptyOption(litreagePair, "brought-into-uk-from-small-producers", default.map{_.importSmall})
      exportCredits  <- askEmptyOption(litreagePair, "claim-credits-for-exports", default.map{_.export})
      wastage        <- askEmptyOption(litreagePair, "claim-credits-for-lost-damaged", default.map{_.wastage})
    } yield SdilReturn(ownBrands,contractPacked,smallProds,imports,importsSmall,exportCredits,wastage)
  }

}
