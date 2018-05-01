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

  // protected def askUpdatedBusinessDetails(
  //   id: String, default: Option[UpdatedBusinessDetails] = None
  // ): WebMonad[UpdatedBusinessDetails] = for {
  //   //TODO: Replace with a single page
  //   orgName <- askString(s"${id}_orgName", default.map { _.tradingName })
  //   address <- askAddress(s"${id}_address", default.map { _.address })
  // } yield UpdatedBusinessDetails(orgName, address)

  // Nasty hack to prevent me from having to either create
  // good function signatures or put '.some' after all the
  // defaults
  private implicit def toSome[A](in: A): Option[A] = in.some

  //


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

  private lazy val deregisterUpdate: WebMonad[VariationData] = {
    throw new NotImplementedError()
  }

  private def getVariation(subscription: RetrievedSubscription): WebMonad[VariationData] = {
    val base = VariationData(subscription)
    for {
      changeType <- askEnum("changeType", ChangeType)
      variation  <- changeType match {
        case ChangeType.Sites      => contactUpdate(base)
        case ChangeType.Activity   => activityUpdate(base)
        case ChangeType.Deregister => deregisterUpdate
      }
    } yield variation
  }

  def errorPage(id: String): WebMonad[Result] = Ok(s"Error $id")


  private def program(subscription: RetrievedSubscription): WebMonad[Result] = for {
    variation <- getVariation(subscription)
    _ <- when (!variation.isMaterialChange) (errorPage("noVariationNeeded"))
    exit <- journeyEnd("variationDone")
  } yield {
    exit
  }

  def index(id: String): Action[AnyContent] = registeredAction.async { implicit request =>

    sdilConnector.retrieveSubscription(request.sdilEnrolment.value) flatMap {
      case Some(s) => runInner(request)(program(s))(id)(dataGet,dataPut)
      case None => NotFound("").pure[Future]
    }
  }

}
