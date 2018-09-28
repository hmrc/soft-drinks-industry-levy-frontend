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

import cats.implicits._
import enumeratum._
import ltbs.play.scaffold.GdsComponents._
import ltbs.play.scaffold.SdilComponents.{packagingSiteMapping, _}
import uk.gov.hmrc.uniform.webmonad._
import play.api.i18n.{Messages, MessagesApi}
import play.api.mvc.{Action, AnyContent, Result}
import play.twirl.api.HtmlFormat
import sdil.actions.RegisteredAction
import sdil.config.AppConfig
import sdil.connectors.SoftDrinksIndustryLevyConnector
import sdil.forms.FormHelpers
import sdil.models._
import sdil.models.backend.Site
import sdil.models.retrieved.RetrievedSubscription
import sdil.models.variations._
import sdil.uniform.SaveForLaterPersistence
import uk.gov.hmrc.http.{HeaderCarrier, NotFoundException}
import uk.gov.hmrc.http.cache.client.ShortLivedHttpCaching
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import uk.gov.hmrc.play.bootstrap.http.FrontendErrorHandler
import uk.gov.hmrc.uniform.playutil.ExtraMessages
import views.html.uniform

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class VariationsController(
  val messagesApi: MessagesApi,
  sdilConnector: SoftDrinksIndustryLevyConnector,
  registeredAction: RegisteredAction,
  cache: ShortLivedHttpCaching,
  errorHandler: FrontendErrorHandler
)(implicit
  val config: AppConfig,
  val ec: ExecutionContext
) extends SdilWMController with FrontendController with FormHelpers with ReturnJourney {

  sealed trait ChangeType extends EnumEntry
  object ChangeType extends Enum[ChangeType] {
    val values = findValues
    case object Returns extends ChangeType
    case object Sites extends ChangeType
    case object Activity extends ChangeType
    case object Deregister extends ChangeType
  }

  private def contactUpdate(
    data: RegistrationVariationData
  ): WebMonad[RegistrationVariationData] = {

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
        manyT("packSites", ask(packagingSiteMapping,_)(packagingSiteForm, implicitly, implicitly, implicitly), default = data
          .updatedProductionSites.toList, min = 1, editSingleForm = Some((packagingSiteMapping, packagingSiteForm))) emptyUnless (data.producer.isLarge.contains(true) || data.copackForOthers)
      } else data.updatedProductionSites.pure[WebMonad]

      warehouses      <- if (change.contains(Sites)) {
        manyT("warehouses", ask(warehouseSiteMapping,_)(warehouseSiteForm, implicitly, implicitly, implicitly), default = data
          .updatedWarehouseSites.toList, editSingleForm = Some((warehouseSiteMapping, warehouseSiteForm)))
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
    data: RegistrationVariationData
  ): WebMonad[RegistrationVariationData] = {


    val litres = litreagePair.nonEmpty("error.litreage.zero")

    for {
      packLarge                   <- askOption(bool, "packLarge")
      useCopacker                 <- ask(bool,"useCopacker", data.usesCopacker) when packLarge.contains(false)
      packageOwn                  <- ask(bool, "packOpt", data.updatedProductionSites.nonEmpty) when packLarge.isDefined
      packQty                     <- ask(litres, "packQty") emptyUnless packageOwn.contains(true)
      copacks                     <- ask(litres, "copackQty") emptyUnless ask(bool, "copacker", data.copackForOthers)
      imports                     <- ask(litres, "importQty") emptyUnless ask(bool, "importer", data.imports)
      noUkActivity                =  (copacks, imports).isEmpty
      smallProducerWithNoCopacker =  packLarge.forall(_ == false) && useCopacker.forall(_ == false)
      shouldDereg                 =  noUkActivity && smallProducerWithNoCopacker
      packer                      =  (packLarge.contains(true) && packageOwn.contains(true)) || !copacks.isEmpty
      variation                   <- if (shouldDereg)
                                       tell("suggestDereg", uniform.confirmOrGoBackTo("suggestDereg", "packLarge")) >> deregisterUpdate(data)
                                     else {
                                        val extraMessages = ExtraMessages(
                                          messages = Map(
                                            "pack-at-business-address.lead" -> s"Registered address: ${Address.fromUkAddress(data.original.address).nonEmptyLines.mkString(", ")}")
                                        )
                                        for {
                                          usePPOBAddress <- ask(bool, "pack-at-business-address")(implicitly, implicitly, extraMessages, implicitly) when packer && data.original.productionSites.isEmpty
                                          pSites = if (usePPOBAddress.getOrElse(false)) {
                                            List(Site.fromAddress(Address.fromUkAddress(data.original.address)))
                                          } else {
                                            data.updatedProductionSites.toList
                                          }
                                          firstPackingSite <- ask(packagingSiteMapping, "first-production-site")(packagingSiteForm, implicitly, ExtraMessages(), implicitly) when
                                            pSites.isEmpty && packer
                                          packSites <- askPackSites(pSites ++ firstPackingSite.fold(List.empty[Site])(x => List(x))) emptyUnless packer


                                          isVoluntary = packLarge.contains(false) && useCopacker.contains(true) && (copacks, imports).isEmpty
                                          warehouses <- askWarehouses(data.updatedWarehouseSites.toList) emptyUnless !isVoluntary
                                        } yield data.copy(
                                          producer = Producer(packLarge.isDefined, packLarge),
                                          usesCopacker = useCopacker.some.flatten,
                                          packageOwn = Some(!packQty.isEmpty),
                                          packageOwnVol = longTupToLitreage(packQty),
                                          copackForOthers = !copacks.isEmpty,
                                          copackForOthersVol = longTupToLitreage(copacks),
                                          imports = !imports.isEmpty,
                                          importsVol = longTupToLitreage(imports),
                                          updatedProductionSites = packSites,
                                          updatedWarehouseSites = warehouses
                                        )
                                      }
                                    } yield variation
  }

  private def deregisterUpdate(
    data: RegistrationVariationData
  ): WebMonad[RegistrationVariationData] = for {
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
  )(implicit hc: HeaderCarrier): WebMonad[Result] = {
    val base = RegistrationVariationData(subscription)
    for {
      variableReturns <- execute(sdilConnector.returns.variable(base.original.utr))
      changeTypes = ChangeType.values.toList.filter(x => variableReturns.nonEmpty || x != ChangeType.Returns)
      changeType <- askOneOf("changeType", changeTypes)
      variation <- changeType match {
        case ChangeType.Returns =>
          resultToWebMonad[RegistrationVariationData](Redirect(routes.VariationsController.adjustments("")))
        case ChangeType.Sites => contactUpdate(base)
        case ChangeType.Activity => activityUpdate(base)
        case ChangeType.Deregister => deregisterUpdate(base)
      }

      path <- getPath
      _ <- checkYourRegAnswers("checkyouranswers", variation, path)
      submission = Convert(variation)
      _ <- execute(sdilConnector.submitVariation(submission, sdilRef)) when submission.nonEmpty
      _ <- clear
      exit <- if(variation.volToMan) {
        journeyEnd("volToMan")
      } else if(variation.manToVol) {
        journeyEnd("manToVol")
      } else journeyEnd("variationDone", whatHappensNext = uniform.fragments.variationsWHN().some)
    } yield exit
  }

  private def chooseReturn(
    subscription: RetrievedSubscription,
    sdilRef: String
  )(implicit hc: HeaderCarrier): WebMonad[Result] = {
    val base = RegistrationVariationData(subscription)
    for {
      variableReturns <- execute(sdilConnector.returns.variable(base.original.utr))
      extraMessages = ExtraMessages(messages = variableReturns.map { x =>
        s"returnPeriod.option.${x.year}${x.quarter}" -> s"${Messages(s"returnPeriod.option.${x.quarter}")} ${x.year}"
      }.toMap)
      returnPeriod <- askOneOf("returnPeriod", variableReturns.sortWith(_>_).map(x => s"${x.year}${x.quarter}"))(extraMessages)
        .map(y => variableReturns.filter(x => x.quarter === y.takeRight(1).toInt && x.year === y.init.toInt).head)
    } yield Redirect(routes.VariationsController.adjustment(year = returnPeriod.year, quarter = returnPeriod.quarter, id = "check-your-variation-answers"))
  }

  private def adjust(
    subscription: RetrievedSubscription,
    sdilRef: String,
    connector: SoftDrinksIndustryLevyConnector,
    returnPeriod: ReturnPeriod
  )(implicit hc: HeaderCarrier): WebMonad[Result] = {
    val base = RegistrationVariationData(subscription)
    implicit val showBackLink: Boolean = false
    for {

      origReturn <- execute(connector.returns.get(base.original.utr, returnPeriod))
        .map(_.getOrElse(throw new NotFoundException(s"No return for ${returnPeriod.year} quarter ${returnPeriod.quarter}")))

      newReturn <- askReturn(base.original, sdilRef, sdilConnector, origReturn.some)

      variation = ReturnVariationData(origReturn, newReturn, returnPeriod, base.original.orgName, base.original.address, "")
      path <- getPath

      broughtForward = BigDecimal("0")
      extraMessages = ExtraMessages(
            messages = Map(
              "heading.check-your-variation-answers" -> s"${Messages(s"returnPeriod.option.${variation.period.quarter}")} ${variation.period.year} return details",
              "return-variation-reason.label" -> s"Reason for correcting ${Messages(s"returnPeriod.option.${variation.period.quarter}")} return"
            ))

      _ <- checkYourReturnAnswers("check-your-variation-answers", variation.revised, broughtForward, base.original, originalReturn = variation.original.some)(extraMessages, false)

      reason <- askBigText(
        "return-variation-reason",
        constraints = List(("error.return-variation-reason.tooLong",
          _.length <= 255)),
        errorOnEmpty = "error.return-variation-reason.empty")(extraMessages)
      repayment <- askOneOf("repayment", List("credit", "bankPayment"))(ltbs.play.scaffold.SdilComponents.extraMessages) when variation.revised.total - variation.original.total < 0
      _ <- checkReturnChanges("check-return-differences", variation.copy(reason = reason, repaymentMethod = repayment))
      _ <- execute(sdilConnector.returns.vary(sdilRef, variation.copy(reason = reason, repaymentMethod = repayment)))
      _ <- clear
      exit <- journeyEnd("returnVariationDone", whatHappensNext = uniform.fragments.variationsWHN(key = Some("return")).some)

    } yield exit
  }

  def checkYourRegAnswers(
                              key: String,
                              v: RegistrationVariationData,
                              path: List[String]): WebMonad[Unit] = {

    val inner = uniform.fragments.variationsCYA(
      v,
      newPackagingSites(v),
      closedPackagingSites(v),
      newWarehouseSites(v),
      closedWarehouseSites(v),
      path

    )
    tell(key, inner)
  }

  def closedWarehouseSites(variation: RegistrationVariationData): List[Site] = {
    closedSites(variation.original.warehouseSites, Convert(variation).closeSites.map(x => x.siteReference))
  }

  def closedPackagingSites(variation: RegistrationVariationData): List[Site] = {
    closedSites(variation.original.productionSites, Convert(variation).closeSites.map(x => x.siteReference))
  }

  def newPackagingSites(variation: RegistrationVariationData): List[Site] = {
    variation.updatedProductionSites.diff(variation.original.productionSites).toList
  }

  def newWarehouseSites(variation: RegistrationVariationData): List[Site] = {
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
        runInner(request)(program(s, sdilRef))(id)(persistence.dataGet,persistence.dataPut, JourneyConfig(SingleStep))
      case None => NotFound("").pure[Future]
    }
  }

  def adjustments(id: String): Action[AnyContent] = registeredAction.async { implicit request =>
    val sdilRef = request.sdilEnrolment.value
    val persistence = SaveForLaterPersistence("adjustments", sdilRef, cache)
    sdilConnector.retrieveSubscription(sdilRef) flatMap {
      case Some(s) =>
        runInner(request)(chooseReturn(s, sdilRef))(id)(persistence.dataGet,persistence.dataPut, JourneyConfig(LeapAhead))
      case None => NotFound("").pure[Future]
    }
  }

  def adjustment(year: Int, quarter: Int, id: String): Action[AnyContent] = registeredAction.async { implicit request =>
    val sdilRef = request.sdilEnrolment.value
    val persistence = SaveForLaterPersistence(s"adjustments-$year$quarter", sdilRef, cache)
    sdilConnector.retrieveSubscription(sdilRef) flatMap {
      case Some(s) =>
        runInner(request)(adjust(s, sdilRef, sdilConnector, ReturnPeriod(year, quarter)))(id)(persistence.dataGet,persistence.dataPut, JourneyConfig(LeapAhead))
      case None => NotFound("").pure[Future]
    }
  }
}
