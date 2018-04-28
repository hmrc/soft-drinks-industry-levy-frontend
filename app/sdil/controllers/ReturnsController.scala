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
import ltbs.play.scaffold._
import play.api.i18n.MessagesApi
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.mvc.{Action, AnyContent, Request, Result}
import play.twirl.api.Html

import scala.concurrent.{ExecutionContext, Future}
import sdil.config.AppConfig
import sdil.connectors.SoftDrinksIndustryLevyConnector
import uk.gov.hmrc.http.cache.client.SessionCache
import webmonad._

import scala.collection.mutable.{Map => MMap}
import play.api.data.Forms._
import views.html.gdspages
import enumeratum._
import sdil.models._
import sdil.models.variations.VariationData

class ReturnsController(val messagesApi: MessagesApi,
                        sdilConnector: SoftDrinksIndustryLevyConnector,
                        keystore: SessionCache)
                       (implicit val config: AppConfig, val ec: ExecutionContext)
    extends SdilWMController {

  sealed trait ChangeType extends EnumEntry
  object ChangeType extends Enum[ChangeType] {
    val values = findValues
    case object Sites extends ChangeType
    case object Activity extends ChangeType
    case object Deregister extends ChangeType
  }

  private lazy val contactUpdate: WebMonad[VariationData] = {
    throw new NotImplementedError()
  }

  implicit class RichWebMonadBoolean(wmb: WebMonad[Boolean]) {
    def andIfTrue[A](next: WebMonad[A]): WebMonad[Option[A]] = for {
      opt <- wmb
      ret <- if (opt) next map {_.some} else none[A].pure[WebMonad]
    } yield ret
  }

  def when[A](b: => Boolean)(wm: WebMonad[A]): WebMonad[Option[A]] =
    if(b) wm.map{_.some} else none[A].pure[WebMonad]

  implicit val siteHtml: HtmlShow[Address] = HtmlShow.instance { address =>
    val lines = address.nonEmptyLines.mkString("<br />")
    Html(s"<div>$lines</div>")
  }

  private val activityUpdate: WebMonad[VariationData] = for {
    packSites    <- manyT("packSites", askAddress(_))
    packLarge    <- askBool("package") andIfTrue askBool("packLarge")
    packQty      <- when (packLarge == Some(true)) (askLitreage("packQty"))
    useCopacker  <- askBool("useCopacker")
    operateSites <- askBool("operateSites")
    imports      <- askBool("importer") andIfTrue askLitreage("importQty")
    copacks      <- askBool("copacker") andIfTrue askLitreage("copackQty")
  } yield VariationData(
    original               = ???,
    updatedBusinessDetails = ???,
    producer               = Producer(packLarge.isDefined, packLarge),
    usesCopacker           = useCopacker.some,
    packageOwn             = packLarge.isDefined.some,
    packageOwnVol          = packQty.map{case (a,b) => Litreage(a,b)},
    copackForOthers        = copacks.isDefined,
    copackForOthersVol     = copacks.map{case (a,b) => Litreage(a,b)},
    imports                = imports.isDefined,
    importsVol             = imports.map{case (a,b) => Litreage(a,b)},
    updatedProductionSites = ???,
    updatedWarehouseSites  = ???,
    updatedContactDetails  = ???,
    previousPages          = Nil
  )

  private lazy val deregisterUpdate: WebMonad[VariationData] = {
    throw new NotImplementedError()
  }

  private val getVariation: WebMonad[VariationData] = for {
    changeType <- askEnum("changeType", ChangeType)
    variation <- changeType match {
      case ChangeType.Sites => contactUpdate
      case ChangeType.Activity => activityUpdate
      case ChangeType.Deregister => deregisterUpdate
    }
  } yield {
    variation
  }

  def errorPage(id: String): WebMonad[Result] = ??? 

  private val program: WebMonad[Result] = for {
    variation <- getVariation
    _ <- when (variation.isMaterialChange) (errorPage("noVariationNeeded"))
  } yield {
    Ok(s"$variation")
  }

  def index(id: String): Action[AnyContent] = run(program)(id)(dataGet,dataPut)

}
