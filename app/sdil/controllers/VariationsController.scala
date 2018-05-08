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
import ltbs.play._
import ltbs.play.scaffold._
import play.api.i18n.MessagesApi
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.mvc._
import play.twirl.api.Html

import scala.concurrent.{ExecutionContext, Future}
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
import sdil.models.variations._

class VariationsController(
  val messagesApi: MessagesApi,
  sdilConnector: SoftDrinksIndustryLevyConnector,
  registeredAction: RegisteredAction,
  keystore: SessionCache
)(implicit
  val config: AppConfig,
  val ec: ExecutionContext
) extends SdilWMController with FrontendController {

  sealed trait ChangeType extends EnumEntry
  object ChangeType extends Enum[ChangeType] {
    val values = findValues
    case object Sites extends ChangeType
    case object Activity extends ChangeType
    case object Deregister extends ChangeType
  }

  implicit val addressHtml: HtmlShow[Address] =
    HtmlShow.instance { address =>
      val lines = address.nonEmptyLines.mkString("<br />")
      Html(s"<div>$lines</div>")
    }

  implicit val siteHtml: HtmlShow[Site] = HtmlShow.instance { site =>
    HtmlShow[Address].showHtml(Address.fromUkAddress(site.address))
  }

  protected def askContactDetails(
    id: String, default: Option[ContactDetails]
  ): WebMonad[ContactDetails] = {
    val contactMapping = mapping(
      "fullName" -> nonEmptyText,
      "position" -> nonEmptyText,
      "phoneNumber" -> nonEmptyText,
      "email" -> nonEmptyText
    )(ContactDetails.apply)(ContactDetails.unapply)

    formPage(id)(contactMapping, default) { (path, b, r) =>
      implicit val request: Request[AnyContent] = r
      gdspages.contactdetails(id, b, path)
    }

  }

  private implicit def toSome[A](in: A): Option[A] = in.some

  private def contactUpdate(
    data: VariationData
  ): WebMonad[VariationData] = for {
    contact         <- askContactDetails("contact", data.updatedContactDetails)
    warehouses      <- manyT("warehouses", askSite(_), default = data.updatedWarehouseSites.toList)
    packSites       <- manyT("packSites", askSite(_), default = data.updatedProductionSites.toList)
    businessAddress <- askAddress("businessAddress", default = data.updatedBusinessAddress)
  } yield data.copy (
    updatedBusinessAddress = businessAddress,
    updatedProductionSites = packSites,
    updatedWarehouseSites  = warehouses,
    updatedContactDetails  = contact
  )


  private def activityUpdate(
    data: VariationData
  ): WebMonad[VariationData] = for {
    packLarge       <- askBool("packLarge", data.producer.isLarge) when askBool("package", data.producer.isProducer)
    packQty         <- askLitreage("packQty") when askBool("packOpt", data.updatedProductionSites.nonEmpty)
    useCopacker     <- askBool("useCopacker", data.usesCopacker)
    copacks         <- askLitreage("copackQty") when askBool("copacker", data.copackForOthers)
    imports         <- askLitreage("importQty") when askBool("importer", data.imports)
    packSites       <- manyT("packSites", askSite(_), default = data.updatedProductionSites.toList)
    warehouses      <- manyT("warehouses", askSite(_), default = data.updatedWarehouseSites.toList)
    businessAddress <- askAddress("businessAddress", default = data.updatedBusinessAddress)
    contact         <- askContactDetails("contact", data.updatedContactDetails)
  } yield data.copy (
    updatedBusinessAddress = businessAddress,
    producer               = Producer(packLarge.isDefined, packLarge),
    usesCopacker           = useCopacker.some,
    packageOwn             = packLarge.isDefined.some,
    packageOwnVol          = packQty.map{case (a,b) => Litreage(a,b)},
    copackForOthers        = copacks.isDefined,
    copackForOthersVol     = copacks.map{case (a,b) => Litreage(a,b)},
    imports                = imports.isDefined,
    importsVol             = imports.map{case (a,b) => Litreage(a,b)},
    updatedProductionSites = packSites,
    updatedWarehouseSites  = warehouses,
    updatedContactDetails  = contact
  )

  private def deregisterUpdate(
    data: VariationData
  ): WebMonad[VariationData] = for {
    reason    <- askBigText("reason")
    deregDate <- askDate("deregDate", none, constraints = List(
                           ("error.deregDate.nopast",  x => x > LocalDate.now),
                           ("error.deregDate.nofuture",x => x < LocalDate.now.plusDays(15))))

  } yield data.copy(
    reason = reason.some,
    deregDate = deregDate.some
  )

  def errorPage(id: String): WebMonad[Result] = Ok(s"Error $id")

  implicit val variationDataShow: HtmlShow[VariationData] = new HtmlShow[VariationData] {
    def showHtml(v: VariationData): Html = views.html.gdspages.variationDataCYA(v)
  }

  private def program(subscription: RetrievedSubscription, sdilRef: String)(implicit hc: HeaderCarrier): WebMonad[Result] = for {
    changeType <- askEnum("changeType", ChangeType)
    base = VariationData(subscription)
    variation  <- changeType match {
      case ChangeType.Sites      => contactUpdate(base)
      case ChangeType.Activity   => activityUpdate(base)
      case ChangeType.Deregister => deregisterUpdate(base)
    }
    _          <- when (!variation.isMaterialChange) (errorPage("noVariationNeeded"))
    _          <- tell("checkyouranswers", variation)
    _          <- execute{sdilConnector.submitVariation(Convert(variation), sdilRef)}
    exit       <- journeyEnd("variationDone")
  } yield {
    exit
  }

  def index(id: String): Action[AnyContent] = registeredAction.async { implicit request =>

    val persistence = SessionCachePersistence("variation", keystore)
    val sdilRef = request.sdilEnrolment.value
    sdilConnector.retrieveSubscription(sdilRef) flatMap {
      case Some(s) => runInner(request)(program(s, sdilRef))(id)(persistence.dataGet,persistence.dataPut)
      case None => NotFound("").pure[Future]
    }


  }

  def index2(id: String): Action[AnyContent] = Action.async { implicit request =>

    val persistence = SessionCachePersistence("test", keystore)

    def testProgram: WebMonad[Result] = for {
      packSites <- manyT("packSites", askSite(_))
      exit      <- journeyEnd("variationDone")
    } yield {
      exit
    }

    runInner(request)(testProgram)(id)(persistence.dataGet,persistence.dataPut)
  }

}
