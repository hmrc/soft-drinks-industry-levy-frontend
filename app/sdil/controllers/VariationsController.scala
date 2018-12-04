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

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalTime, ZoneId}

import cats.implicits._
import enumeratum._
import java.time.format.DateTimeFormatter._

import ltbs.play.scaffold.GdsComponents._
import ltbs.play.scaffold.SdilComponents
import ltbs.play.scaffold.SdilComponents.ProducerType.{Large, Small}
import ltbs.play.scaffold.SdilComponents.{packagingSiteMapping, litreageForm => approxLitreageForm, _}
import play.api.data.format.Formatter
import play.api.data.{FormError, Forms, Mapping}
import uk.gov.hmrc.uniform.webmonad._
import play.api.i18n.{Messages, MessagesApi}
import play.api.libs.json.{Format, Json, Writes}
import play.api.mvc.{Action, AnyContent, Request, Result}
import play.twirl.api.{Html, HtmlFormat}
import uk.gov.hmrc.uniform.playutil
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
import uk.gov.hmrc.uniform.HtmlShow
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

    def askContactChangeType: WebMonad[Set[ContactChangeType]] =
      if (data.isVoluntary) {
        askSet(
          "contact-change-type",
          Set(ContactChangeType.ContactPerson, ContactChangeType.ContactAddress),
          minSize = 1,
          None,
          Html(Messages("change.latency")).some
        )
      } else {
        askSet(
          "contact-change-type",
          ContactChangeType.values.toSet,
          minSize = 1,
          None,
          Html(Messages("change.latency")).some
        )
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
      packLarge                   <- askOneOf("packLarge", ProducerType.values.toList) map {
                                        case Large => Some(true)
                                        case Small => Some(false)
                                        case _ => None
                                      }
      useCopacker                 <- ask(bool("useCopacker"),"useCopacker", data.usesCopacker) when packLarge.contains(false)
      packageOwn                  <- askOption(litreagePair.nonEmpty, "packOpt")(approxLitreageForm, implicitly, implicitly) when packLarge.nonEmpty
      copacks                     <- askOption(litreagePair.nonEmpty, "copacker")(approxLitreageForm, implicitly, implicitly)
      imports                     <- askOption(litreagePair.nonEmpty, "importer")(approxLitreageForm, implicitly, implicitly)
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
                                          usePPOBAddress <- ask(bool(), "pack-at-business-address")(implicitly, implicitly, extraMessages, implicitly) when packer && data.original.productionSites.isEmpty
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
                                          packageOwn = Some(packageOwn.isDefined),
                                          packageOwnVol = longTupToLitreage(packageOwn.flatten.getOrElse((0,0))),
                                          copackForOthers = copacks.isDefined,
                                          copackForOthersVol = longTupToLitreage(copacks.getOrElse((0,0))),
                                          imports = imports.isDefined,
                                          importsVol = longTupToLitreage(imports.getOrElse((0,0))),
                                          updatedProductionSites = packSites,
                                          updatedWarehouseSites = warehouses
                                        )
                                      }
                                    } yield variation
  }

  private def deregisterUpdate(
    data: RegistrationVariationData
  ): WebMonad[RegistrationVariationData] = {
    val extraMessages = ExtraMessages(
      messages =
        Map("heading.cancel-registration-reason.orgName" -> s"${data.original.orgName}",
            "heading.cancel-registration-date.orgName" -> s"${data.original.orgName}"))

    for {
      reason <- askBigText(
        "cancel-registration-reason",
        constraints = List(("error.deregReason.tooLong", _.length <= 255)),
        errorOnEmpty = "error.cancel-registration-reason.empty")(extraMessages)

      deregDate <- ask(startDate
        .verifying(
          "error.cancel-registration-date.nopast",
          _ >= LocalDate.now)
        .verifying(
          "error.cancel-registration-date.nofuture",
          _ < LocalDate.now.plusDays(15)),
        "cancel-registration-date")(implicitly, implicitly, extraMessages, implicitly)
    } yield data.copy(
      reason = reason.some,
      deregDate = deregDate.some
    )
  }
  private def fileReturnsBeforeDereg[A](returnPeriods: List[ReturnPeriod], data: RegistrationVariationData) =
    for {
      sendToReturns <- tell("file-return-before-deregistration", uniform.fragments.return_before_dereg("file-return-before-deregistration", returnPeriods))
      _ <- resultToWebMonad[A](Redirect(routes.ServicePageController.show()))
    } yield data

  private def program(
    subscription: RetrievedSubscription,
    sdilRef: String
  )(implicit hc: HeaderCarrier): WebMonad[Result] = {
    val base = RegistrationVariationData(subscription)

    for {
      variableReturns <- execute(sdilConnector.returns.variable(base.original.utr))
      returnPeriods <- execute(sdilConnector.returns.pending(subscription.utr))
      onlyReturns = subscription.deregDate.nonEmpty
      changeTypes = ChangeType.values.toList.filter(x => variableReturns.nonEmpty || x != ChangeType.Returns)
      changeType <- askOneOf("select-change", changeTypes, helpText = Html(Messages("change.latency")).some) when !onlyReturns
      variation <- changeType match {
          //TODO Ask Matt why this None is here
        case None | Some(ChangeType.Returns) =>
          chooseReturn(subscription, sdilRef)
        case Some(ChangeType.Sites) => contactUpdate(base)
        case Some(ChangeType.Activity) => activityUpdate(base)
        case Some(ChangeType.Deregister) if returnPeriods.isEmpty => deregisterUpdate(base)
        case Some(ChangeType.Deregister) if returnPeriods.nonEmpty => fileReturnsBeforeDereg(returnPeriods, base)
      }

      path <- getPath
      extraMessages = ExtraMessages(
        messages =
        if(variation.deregDate.nonEmpty) {
          Map(
            "heading.check-answers" -> Messages("heading.check-answers.dereg"),
            "heading.check-answers.orgName" -> s"${subscription.orgName},",
            "variationDone.title" -> Messages("deregDone.title"),
            "variationDone.subheading" -> Messages("deregDone.subtitle")
          )
        } else {
          Map("heading.check-answers.orgName" -> s"${subscription.orgName}")
        }
      )
      _ <- checkYourRegAnswers("check-answers", variation, path)(extraMessages)
      submission = Convert(variation)
      _ <- execute(sdilConnector.submitVariation(submission, sdilRef)) when submission.nonEmpty
      _ <- clear
      subheading = Html(
        Messages(
          if(variation.deregDate.nonEmpty) {
            "variationDone.your.request"
          } else {
            "returnVariationDone.your.updates"
          },
          subscription.orgName,
          LocalDate.now.format(ofPattern("d MMMM yyyy")),
          LocalTime.now(ZoneId.of("Europe/London")).format(DateTimeFormatter.ofPattern("h:mma")).toLowerCase)).some
      whnChangeLiability = Messages("return-sent.servicePage", sdil.controllers.routes.ServicePageController.show())
      whnVolToMan = Html(Messages("volToMan.what-happens-next") ++ whnChangeLiability)
      whnManToVol = Html(Messages("manToVol.what-happens-next") ++ whnChangeLiability)
      exit <- if(variation.volToMan) {
        journeyEnd("volToMan", LocalDate.now, subheading, whnVolToMan.some)(extraMessages)
      } else if(variation.manToVol) {
        journeyEnd("manToVol", LocalDate.now, subheading, whnManToVol.some)(extraMessages)
      } else journeyEnd(
        id = "variationDone",
        subheading = subheading,
        whatHappensNext = uniform.fragments.variationsWHN(variation.deregDate).some)(extraMessages)
    } yield exit
  }

  private def chooseReturn[A](
    subscription: RetrievedSubscription,
    sdilRef: String
  )(implicit hc: HeaderCarrier): WebMonad[A] = {
    val base = RegistrationVariationData(subscription)
    for {
      variableReturns <- execute(sdilConnector.returns.variable(base.original.utr))
      messages = variableReturns.map { x =>
        s"returnPeriod.option.${x.year}${x.quarter}" -> s"${Messages(s"returnPeriod.option.${x.quarter}")} ${x.year}"
      }.toMap ++ Map("error.radio-form.choose-option" -> "error.radio-form.choose-option.returnPeriod")

      extraMessages = ExtraMessages(messages)

      returnPeriod <- askOneOf("returnPeriod", variableReturns.sortWith(_>_).map(x => s"${x.year}${x.quarter}"))(extraMessages)
        .map(y => variableReturns.filter(x => x.quarter === y.takeRight(1).toInt && x.year === y.init.toInt).head)
      _ <- clear
      _ <- resultToWebMonad[A](Redirect(routes.VariationsController.adjustment(year = returnPeriod.year, quarter = returnPeriod.quarter, id = "check-your-variation-answers")))
    } yield throw new IllegalStateException("we shouldn't be here")
  }

  private def adjust(
    subscription: RetrievedSubscription,
    sdilRef: String,
    connector: SoftDrinksIndustryLevyConnector,
    returnPeriod: ReturnPeriod
  )(implicit hc: HeaderCarrier): WebMonad[Result] = {
    val base = RegistrationVariationData(subscription)
    implicit val showBackLink: ShowBackLink = ShowBackLink(false)
    for {

      origReturn <- execute(connector.returns.get(base.original.utr, returnPeriod))
        .map(_.getOrElse(throw new NotFoundException(s"No return for ${returnPeriod.year} quarter ${returnPeriod.quarter}")))

      newReturn <- askReturn(base.original, sdilRef, sdilConnector, returnPeriod, origReturn.some)

      variation = ReturnVariationData(origReturn, newReturn, returnPeriod, base.original.orgName, base.original.address, "")
      path <- getPath
//TODO Brought forward should not be 0
      broughtForward = BigDecimal("0")
      extraMessages = ExtraMessages(
            messages = Map(
              "heading.check-your-variation-answers" -> s"${Messages(s"returnPeriod.option.${variation.period.quarter}")} ${variation.period.year} return details",
              "return-variation-reason.label" -> s"Reason for correcting ${Messages(s"returnPeriod.option.${variation.period.quarter}")} ${variation.period.year} return",
              "heading.check-answers.orgName" -> s"${subscription.orgName}"
            ))

      isSmallProd <- execute(isSmallProducer(sdilRef, sdilConnector, returnPeriod))

      _ <- checkYourReturnAnswers("check-your-variation-answers", variation.revised, broughtForward, base.original, isSmallProd, originalReturn = variation.original.some)(extraMessages, implicitly)

      reason <- askBigText(
        "return-variation-reason",
        constraints = List(("error.return-variation-reason.tooLong",
          _.length <= 255)),
        errorOnEmpty = "error.return-variation-reason.empty")(extraMessages)
      repayment <- askOneOf("repayment", List("credit", "bankPayment"))(ltbs.play.scaffold.SdilComponents.extraMessages) when variation.revised.total - variation.original.total < 0
      _ <- checkReturnChanges("check-return-differences", variation.copy(reason = reason, repaymentMethod = repayment))
      _ <- execute(sdilConnector.returns.vary(sdilRef, variation.copy(reason = reason, repaymentMethod = repayment)))
      _ <- clear
      subheading = Html(
        Messages(
          "returnVariationDone.your.updates",
          subscription.orgName,
          LocalDate.now.format(ofPattern("d MMMM yyyy")),
          LocalTime.now(ZoneId.of("Europe/London")).format(DateTimeFormatter.ofPattern("h:mma")).toLowerCase)).some

      exit <- journeyEnd(
        id = "returnVariationDone",
        subheading = subheading,
        whatHappensNext = uniform.fragments.variationsWHN(key = Some("return")).some)(extraMessages)

    } yield exit
  }

  def checkYourRegAnswers(
    key: String,
    v: RegistrationVariationData,
    path: List[String])(implicit extraMessages: ExtraMessages): WebMonad[Unit] = {
    
    val inner = uniform.fragments.variationsCYA(
      v,
      newPackagingSites(v),
      closedPackagingSites(v),
      newWarehouseSites(v),
      closedWarehouseSites(v),
      path
    )
    tell(key, inner)(implicitly, extraMessages)
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

  private def changeBusinessAddressTemplate(
    id: String,
    subscription: RetrievedSubscription
  )(implicit extraMessages: ExtraMessages): WebMonad[Unit] = {

    val unitMapping: Mapping[Unit] = Forms.of[Unit](new Formatter[Unit] {
      override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Unit] = Right(())

      override def unbind(key: String, value: Unit): Map[String, String] = Map.empty
    })

    formPage(id)(unitMapping, none[Unit]){  (path, form, r) =>
      implicit val request: Request[AnyContent] = r

    uniform.fragments.update_business_addresses(
      id,
      form,
      path,
      subscription,
      Address.fromUkAddress(subscription.address))
    }
  }

  def changeBusinessAddress(id: String): Action[AnyContent] = registeredAction.async { implicit request =>
    val sdilRef = request.sdilEnrolment.value
    val persistence = SaveForLaterPersistence("variations", sdilRef, cache)
    sdilConnector.retrieveSubscription(sdilRef) flatMap {
      case Some(s) =>
        runInner(request)(changeBusinessAddressJourney(s, sdilRef))(id)(persistence.dataGet,persistence.dataPut, JourneyConfig(SingleStep))
      case None => NotFound("").pure[Future]
    }
  }

  private def changeBusinessAddressJourney (
    subscription: RetrievedSubscription,
    sdilRef: String
  )(implicit hc: HeaderCarrier): WebMonad[Result] = {
    val base = RegistrationVariationData(subscription)

    for {
      _ <- changeBusinessAddressTemplate("change-registered-account-details", subscription )
      variation <- contactUpdate(base)
      path <- getPath
      _ <- checkYourRegAnswers("checkyouranswers", variation, path)
      submission = Convert(variation)
      _ <- execute(sdilConnector.submitVariation(submission, sdilRef)) when submission.nonEmpty
      _ <- clear
      exit <- journeyEnd("variationDone", whatHappensNext = uniform.fragments.variationsWHN().some)
    } yield exit
  }

  def changeActorStatus(id: String): Action[AnyContent] = registeredAction.async { implicit request =>
    val sdilRef = request.sdilEnrolment.value
    val persistence = SaveForLaterPersistence("variations", sdilRef, cache)
    sdilConnector.retrieveSubscription(sdilRef) flatMap {
      case Some(s) =>
        runInner(request)(changeActorStatusJourney(s, sdilRef))(id)(persistence.dataGet,persistence.dataPut, JourneyConfig(SingleStep))
      case None => NotFound("").pure[Future]
    }
  }


  private def changeActorStatusJourney(
    subscription: RetrievedSubscription,
    sdilRef: String
  )(implicit hc: HeaderCarrier): WebMonad[Result] = {
    val base = RegistrationVariationData(subscription)

    for {
      variation <- activityUpdate(base)

      path <- getPath
      _ <- checkYourRegAnswers("checkyouranswers", variation, path)
      submission = Convert(variation)
      _ <- execute(sdilConnector.submitVariation(submission, sdilRef)) when submission.nonEmpty
      _ <- clear
      exit <- if (variation.volToMan) {
        journeyEnd("volToMan")
      } else if (variation.manToVol) {
        journeyEnd("manToVol")
      } else journeyEnd("variationDone", whatHappensNext = uniform.fragments.variationsWHN().some)
    } yield exit
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
