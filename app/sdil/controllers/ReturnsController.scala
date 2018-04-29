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

class ReturnsController(
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

  private lazy val contactUpdate: WebMonad[VariationData] = {
    throw new NotImplementedError()
  }

  implicit val siteHtml: HtmlShow[Address] =
    HtmlShow.instance { address =>
      val lines = address.nonEmptyLines.mkString("<br />")
      Html(s"<div>$lines</div>")
    }

  protected def askContactDetails(
    id: String, default: Option[ContactDetails]
  ): WebMonad[ContactDetails] = for {
    //TODO: Replace with a single page
    name     <- askString(s"${id}_name", default.map{_.fullName})
    position <- askString(s"${id}_position", default.map{_.position})
    phone    <- askString(s"${id}_phone", default.map{_.phoneNumber})
    email    <- askString(s"${id}_email", default.map{_.email})
  } yield ContactDetails(name, position, phone, email)

  protected def askUpdatedBusinessDetails(
    id: String, default: Option[UpdatedBusinessDetails] = None
  ): WebMonad[UpdatedBusinessDetails] = for {
    //TODO: Replace with a single page
    orgName <- askString(s"${id}_orgName", default.map { _.tradingName })
    address <- askAddress(s"${id}_address", default.map { _.address })
  } yield UpdatedBusinessDetails(orgName, address)

  // Nasty hack to prevent me from having to either create
  // good function signatures or put '.some' after all the
  // defaults
  private implicit def toSome[A](in: A): Option[A] = in.some

  //

  private def activityUpdate(
    data: VariationData
  ): WebMonad[VariationData] = for {
    packSites       <- manyT("packSites", askAddress(_), default = data.updatedProductionSites.toList)
    packLarge       <- askBool("packLarge", data.producer.isLarge) when
                         askBool("package", data.producer.isProducer)
    packQty         <- askLitreage("packQty") when (packLarge == Some(true))
    // packSites       <- if (packLarge == Some(true))
    //                      manyT("packSites", askAddress(_), default = data.updatedProductionSites.toList)
    //                        else
    //                      List.empty[Address].pure[WebMonad]
    useCopacker     <- askBool("useCopacker", data.usesCopacker)
    imports         <- askLitreage("importQty") when
                         askBool("importer", data.imports)
    copacks         <- askLitreage("copackQty") when
                         askBool("copacker", data.copackForOthers)
    contact         <- askContactDetails("contact", data.updatedContactDetails)
    businessDetails <- askUpdatedBusinessDetails("updatedBusinessDetails",
                         data.updatedBusinessDetails)
    warehouses      <- manyT("warehouses", askAddress(_), default = data.updatedWarehouseSites.toList)
  } yield data.copy (
    updatedBusinessDetails = businessDetails,
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
    updatedContactDetails  = contact,
    previousPages          = Nil
  )

  private lazy val deregisterUpdate: WebMonad[VariationData] = {
    throw new NotImplementedError()
  }

  // Retrieve from ETMP
  private def retrieveSubscription: WebMonad[RetrievedSubscription] =
    RetrievedSubscription(
      utr             = "UTR12345",
      orgName         = "eCola Group",
      address         = UkAddress(List("12 The Street", "Blahville"),"AK47 9PG"),
      activity        = RetrievedActivity(true, false, true, true, false),
      liabilityDate   = LocalDate.now,
      productionSites = List(
        Site(UkAddress(List("12 The Street", "Blahville"),"AK47 9PG"))),
      warehouseSites  = List.empty[Site],
      contact         = Contact(
        "Joe McBlokey".some,
        "Carbonated Drinks Evangelist".some,
        "01234 567890",
        "joe@ecola.co.uk")
    ).pure[WebMonad]

  private def getVariation(implicit hc: HeaderCarrier): WebMonad[VariationData] = for {
    //    baseP      <- httpGet[RetrievedSubscription]("subscription", s"$sdilUrl/subscription/sdil/1234")
    base       <- cachedFutureOpt("subscription"){ sdilConnector.retrieveSubscription("XKSDIL000000000")}.
                     map{x => VariationData.apply(x.get)}

    //base       <- retrieveSubscription.map{VariationData.apply}
    changeType <- askEnum("changeType", ChangeType)
    variation  <- changeType match {
      case ChangeType.Sites      => contactUpdate
      case ChangeType.Activity   => activityUpdate(base)
      case ChangeType.Deregister => deregisterUpdate
    }
  } yield variation

  def errorPage(id: String): WebMonad[Result] = Ok(s"Error $id")


  private def program(implicit hc: HeaderCarrier): WebMonad[Result] = for {
    variation <- getVariation
    _ <- when (!variation.isMaterialChange) (errorPage("noVariationNeeded"))
  } yield {
    Ok(s"$variation")
  }

  def index(id: String): Action[AnyContent] = registeredAction.async { implicit request =>
    runInner(request)(program)(id)(dataGet,dataPut)
  }

}
