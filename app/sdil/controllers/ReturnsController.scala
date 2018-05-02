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

import cats.implicits._
import java.time.LocalDate
import ltbs.play.scaffold._
import play.api.i18n.MessagesApi
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.mvc._
import play.twirl.api.Html
import scala.concurrent.{ ExecutionContext, Future }
import sdil.actions.RegisteredAction
import sdil.config._
import sdil.connectors.SoftDrinksIndustryLevyConnector
import sdil.models.backend.{ Contact, Site, UkAddress }
import sdil.models.retrieved.{ RetrievedActivity, RetrievedSubscription }
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import webmonad._
import scala.collection.mutable.{Map => MMap}
import play.api.data.Forms._
import views.html.gdspages
import enumeratum._
import sdil.models._
import play.api.data.Mapping
import scala.util.Try

class ReturnsController (
  val messagesApi: MessagesApi,
  sdilConnector: SoftDrinksIndustryLevyConnector,
  registeredAction: RegisteredAction,
  keystore: SessionCache
)(implicit
  val config: AppConfig,
  val ec: ExecutionContext
) extends SdilWMController with FrontendController {

  implicit val address: Format[SmallProducer] = Json.format[SmallProducer]

  implicit val smallProducerHtml: HtmlShow[SmallProducer] =
    HtmlShow.instance { producer =>
      Html(s"${producer.sdilRef} ${producer.litreage}")
    }

  protected def askSmallProducer(
    id: String,
    default: Option[SmallProducer] = None
  ): WebMonad[SmallProducer] = {



    val litreage: Mapping[Long] = nonEmptyText
      .transform[String](_.replaceAll(",", ""), _.toString)
      .verifying("error.litreage.numeric", l => Try(BigDecimal.apply(l)).isSuccess)
      .transform[BigDecimal](BigDecimal.apply, _.toString)
      .verifying("error.litreage.numeric", _.isWhole)
      .verifying("error.litreage.max", _ <= 9999999999999L)
      .verifying("error.litreage.min", _ >= 0)
      .transform[Long](_.toLong, BigDecimal.apply)

    formPage(id)(
      mapping(
        "sdilRef" -> nonEmptyText,
        "lower" -> litreage,
        "higher" -> litreage){
        (ref,l,h) => SmallProducer(ref, (l,h))
      }{
        case SmallProducer(ref, (l,h)) => (ref,l,h).some
      },
      default
    ){ (path,b,r) =>
      implicit val request: Request[AnyContent] = r
      gdspages.smallProducer(id, b, path)
    }
  }

  /** 
    *
    */
  def askLitreageOpt(key: String): WebMonad[(Long, Long)] =
    askLitreage(key + "Qty") emptyUnless askBool(key + "YesNo")

  private val program: WebMonad[Result] = for {
    packageSmall <- manyT("packageSmallEntries", askSmallProducer(_)) emptyUnless askBool("packageSmallYN")
    packageLarge <- askLitreageOpt("packageLarge")
    importSmall  <- askLitreageOpt("importSmall")
    importLarge  <- askLitreageOpt("importLarge")
    export       <- askLitreageOpt("export")
    wastage      <- askLitreageOpt("wastage")
    exit         <- journeyEnd("returnsDone")
  } yield {
    exit
  }

  def index(id: String): Action[AnyContent] = Action.async { implicit request =>
    runInner(request)(program)(id)(dataGet,dataPut)
  }
}
