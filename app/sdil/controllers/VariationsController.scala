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

import java.time.LocalDate

import cats.data.EitherT
import cats.implicits._
import enumeratum._
import ltbs.play.scaffold.GdsComponents._
import ltbs.play.scaffold.SdilComponents._
import ltbs.play.scaffold.webmonad._
import play.api.i18n.MessagesApi
import play.api.libs.json.{JsValue, _}
import play.api.mvc.{Action, _}
import sdil.actions.RegisteredAction
import sdil.config._
import sdil.connectors.SoftDrinksIndustryLevyConnector
import sdil.forms.FormHelpers
import sdil.models._
import sdil.models.backend.Site
import sdil.models.retrieved.RetrievedSubscription
import sdil.models.variations._
import sdil.uniform.SaveForLaterPersistence
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.{SessionCache, ShortLivedHttpCaching}
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import views.html.uniform
import ContactDetailsForm.contactDetailsMapping

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
class VariationsController(
  val messagesApi: MessagesApi,
  sdilConnector: SoftDrinksIndustryLevyConnector,
  registeredAction: RegisteredAction,
  keystore: SessionCache,
  cache: ShortLivedHttpCaching
)(implicit
  val config: AppConfig,
  val ec: ExecutionContext
) extends SdilWMController with FrontendController with FormHelpers {

  sealed trait ChangeType extends EnumEntry
  object ChangeType extends Enum[ChangeType] {
    val values = findValues
    case object Sites extends ChangeType
    case object Activity extends ChangeType
    case object Deregister extends ChangeType
  }

  private def contactUpdate(
    data: VariationData
  ): WebMonad[VariationData] = {

    sealed trait ContactChangeType extends EnumEntry
    object ContactChangeType extends Enum[ContactChangeType] {
      val values = findValues
      case object Sites extends ContactChangeType
      case object ContactPerson extends ContactChangeType
      case object ContactAddress extends ContactChangeType
    }

    def askContactChangeType: WebMonad[Set[ContactChangeType]] = if (data.isVoluntary) {
      askSet("contactChangeType", Set(ContactChangeType.ContactPerson, ContactChangeType.ContactAddress), minSize = 1)
    } else {
      askSet("contactChangeType", ContactChangeType.values.toSet, minSize = 1)
    }

    import ContactChangeType._
    for {

      change          <- askContactChangeType

      packSites       <- if (change.contains(Sites)) {
        manyT("packSites", ask(packagingSiteMapping,_)(packagingSiteForm, implicitly), default = data
          .updatedProductionSites.toList, min = 1) emptyUnless (data.producer.isLarge.contains(true) || data.copackForOthers)
      } else data.updatedProductionSites.pure[WebMonad]

      warehouses      <- if (change.contains(Sites)) {
        manyT("warehouses", ask(warehouseSiteMapping,_)(warehouseSiteForm, implicitly), default = data.updatedWarehouseSites.toList)
      } else data.updatedWarehouseSites.pure[WebMonad]

      contact         <- if (change.contains(ContactPerson)) {
        ask(contactDetailsMapping, "contact", data.updatedContactDetails)
      } else data.updatedContactDetails.pure[WebMonad]

      businessAddress <- if (change.contains(ContactAddress)) {
        ask(addressMapping, "businessAddress", default = data.updatedBusinessAddress)
      } else data.updatedBusinessAddress.pure[WebMonad]

    } yield data.copy (
      updatedBusinessAddress = businessAddress,
      updatedProductionSites = packSites,
      updatedWarehouseSites  = warehouses,
      updatedContactDetails  = contact
    )
  }

  private def activityUpdate(
    data: VariationData
  ): WebMonad[VariationData] = {


    val litres = litreagePair.nonEmpty("error.litreage.zero")

    for {
      packLarge                   <- askOption(bool, "packLarge")
      useCopacker                 <- ask(bool,"useCopacker", data.usesCopacker) when packLarge.contains(false)
      packageOwn                  <- ask(bool, "packOpt", data.updatedProductionSites.nonEmpty) when packLarge.isDefined
      packQty                     <- ask(litres, "packQty") emptyUnless packageOwn.contains(true)
      copacks                     <- ask(litres, "copackQty") emptyUnless ask(bool, "copacker", data.copackForOthers)
      imports <- ask(litres, "importQty") emptyUnless ask(bool, "importer", data.imports)      
      noUkActivity                =  (copacks, imports).isEmpty
      smallProducerWithNoCopacker =  packLarge.forall(_ == false) && useCopacker.forall(_ == false)
      shouldDereg                 =  noUkActivity && smallProducerWithNoCopacker
      variation                   <- if (shouldDereg)
                                       tell("suggestDereg", uniform.confirmOrGoBackTo("suggestDereg", "packLarge")) >> deregisterUpdate(data)
                                     else for {
                                       packSites       <- askPackSites(data.updatedProductionSites.toList, (packLarge.contains(true) && packageOwn.contains(true)) || !copacks.isEmpty)
                                       isVoluntary     =  packLarge.contains(false) && useCopacker.contains(true) && (copacks, imports).isEmpty
                                       warehouses      <- manyT("warehousesActivity", ask(warehouseSiteMapping,_)(warehouseSiteForm, implicitly), default = data.updatedWarehouseSites.toList) emptyUnless !isVoluntary
                                     } yield data.copy (
                                       producer               = Producer(packLarge.isDefined, packLarge),
                                       usesCopacker           = useCopacker.some.flatten,
                                       packageOwn             = Some(!packQty.isEmpty),
                                       packageOwnVol          = longTupToLitreage(packQty),
                                       copackForOthers        = !copacks.isEmpty,
                                       copackForOthersVol     = longTupToLitreage(copacks),
                                       imports                = !imports.isEmpty,
                                       importsVol             = longTupToLitreage(imports),
                                       updatedProductionSites = packSites,
                                       updatedWarehouseSites  = warehouses
                                     )
    } yield variation
  }

  private def deregisterUpdate(
    data: VariationData
  ): WebMonad[VariationData] = for {
    reason    <- askBigText("reason", constraints = List(("error.deregReason.tooLong", _.length <= 255)), errorOnEmpty = "error.reason.empty")
    deregDate <- ask(startDate
      .verifying("error.deregDate.nopast",  _ >= LocalDate.now)
      .verifying("error.deregDate.nofuture",_ <  LocalDate.now.plusDays(15)), "deregDate")
  } yield data.copy(
    reason = reason.some,
    deregDate = deregDate.some
  )

  private def program(
    subscription: RetrievedSubscription,
    sdilRef: String
  )(implicit hc: HeaderCarrier): WebMonad[Result] = for {
    changeType <- askOneOf("changeType", List(ChangeType.Sites, ChangeType.Deregister))
    base = VariationData(subscription)
    variation  <- changeType match {
      case ChangeType.Sites => contactUpdate(base)
      case ChangeType.Activity   => activityUpdate(base)
      case ChangeType.Deregister => deregisterUpdate(base)
    }
    path <- getPath
    cya = uniform.fragments.variationsCYA(
      variation,
      newPackagingSites(variation),
      closedPackagingSites(variation),
      newWarehouseSites(variation),
      closedWarehouseSites(variation),
      path
    )
    _ <- if (variation.noVariation) end("noVariation", cya) else tell("checkyouranswers", cya)
    submission = Convert(variation)
    _    <- execute(sdilConnector.submitVariation(submission, sdilRef)) when submission.nonEmpty
    _    <- clear
    exit <- variation match {
      case a if a.volToMan => journeyEnd("volToMan")
      case b if b.manToVol => journeyEnd("manToVol")
      case _ => journeyEnd("variationDone")
    }
  } yield {
    exit
  }

  def closedWarehouseSites(variation: VariationData): List[Site] = {
    closedSites(variation.original.warehouseSites, Convert(variation).closeSites.map(x => x.siteReference))
  }

  def closedPackagingSites(variation: VariationData): List[Site] = {
    closedSites(variation.original.productionSites, Convert(variation).closeSites.map(x => x.siteReference))
  }

  def newPackagingSites(variation: VariationData): List[Site] = {
    variation.updatedProductionSites.diff(variation.original.productionSites).toList
  }

  def newWarehouseSites(variation: VariationData): List[Site] = {
    variation.updatedWarehouseSites.diff(variation.original.warehouseSites).toList
  }

  private def closedSites(sites: List[Site], closedSites: List[String]): List[Site] = {
    sites.filter { x =>
      x.closureDate.fold(true) {
        _.isAfter(LocalDate.now)
      }
    }.filter(x => closedSites.contains(x.ref.getOrElse("")))
  }

  def index(id: String): Action[AnyContent] = registeredAction.async { implicit request =>
    val sdilRef = request.sdilEnrolment.value
    val persistence = SaveForLaterPersistence("variations", sdilRef, cache)
    sdilConnector.retrieveSubscription(sdilRef) flatMap {
      case Some(s) =>
        runInner(request)(program(s, sdilRef))(id)(persistence.dataGet,persistence.dataPut)
      case None => NotFound("").pure[Future]
    }
  }

}
