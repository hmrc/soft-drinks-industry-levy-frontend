/*
 * Copyright 2019 HM Revenue & Customs
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
import java.time.format.DateTimeFormatter._
import java.time.{LocalDate, LocalTime, ZoneId}

import cats.implicits._
import enumeratum._
import ltbs.play.scaffold.GdsComponents._
import ltbs.play.scaffold.SdilComponents.ProducerType.{Large, Small}
import ltbs.play.scaffold.SdilComponents.{packagingSiteMapping, litreageForm => approxLitreageForm, _}
import play.api.data.format.Formatter
import play.api.data.{FormError, Forms, Mapping}
import play.api.i18n.{Messages, MessagesApi, MessagesProvider}
import play.api.mvc._
import play.twirl.api.Html
import sdil.actions.RegisteredAction
import sdil.config.AppConfig
import sdil.connectors.SoftDrinksIndustryLevyConnector
import sdil.forms.FormHelpers
import sdil.models._
import sdil.models.backend.Site
import sdil.models.retrieved.RetrievedSubscription
import sdil.models.variations._
import sdil.uniform.SaveForLaterPersistence
import uk.gov.hmrc.http.cache.client.ShortLivedHttpCaching
import uk.gov.hmrc.http.{HeaderCarrier, NotFoundException}
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import uk.gov.hmrc.play.bootstrap.http.FrontendErrorHandler
import uk.gov.hmrc.uniform.playutil.ExtraMessages
import uk.gov.hmrc.uniform.webmonad._
import views.html.uniform

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class VariationsController(
  override val messagesApi: MessagesApi,
  val sdilConnector: SoftDrinksIndustryLevyConnector,
  registeredAction: RegisteredAction,
  cache: ShortLivedHttpCaching,
  errorHandler: FrontendErrorHandler,
  mcc: MessagesControllerComponents
)(implicit
  val config: AppConfig,
  val ec: ExecutionContext
) extends FrontendController(mcc) with SdilWMController with FormHelpers with ReturnJourney {

  override lazy val parse = mcc.parsers

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
  )(implicit extraMessages: ExtraMessages): WebMonad[RegistrationVariationData] = {

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
          "change-registered-details",
          Set(ContactChangeType.ContactPerson, ContactChangeType.ContactAddress),
          minSize = 1,
          None,
          Html(Messages("change.latency")).some
        )
      } else {
        askSet(
          "change-registered-details",
          ContactChangeType.values.toSet,
          minSize = 1,
          None,
          Html(Messages("change.latency")).some
        )
    }

    import ContactChangeType._

    val extraMessages = ExtraMessages(
      messages =
        Map("heading.contact-details" -> Messages("heading.contact"))
    )

    for {

      change          <- askContactChangeType

      packSites       <- if (change.contains(Sites)) {
        manyT("packaging-site-details", ask(packagingSiteMapping,_)(packagingSiteForm, implicitly, extraMessages, implicitly), default = data
          .updatedProductionSites.toList, min = 1, editSingleForm = Some((packagingSiteMapping, packagingSiteForm))) emptyUnless (data.producer.isLarge.contains(true) || data.copackForOthers)
      } else data.updatedProductionSites.pure[WebMonad]

      warehouses      <- if (change.contains(Sites)) {
        manyT("warehouse-details", ask(warehouseSiteMapping,_)(warehouseSiteForm, implicitly, extraMessages, implicitly), default = data
          .updatedWarehouseSites.toList, editSingleForm = Some((warehouseSiteMapping, warehouseSiteForm)))
      } else data.updatedWarehouseSites.pure[WebMonad]

      contact         <- if (change.contains(ContactPerson)) {
        ask(contactDetailsMapping, "contact-details", data.updatedContactDetails)(implicitly, implicitly, extraMessages, implicitly)
      } else data.updatedContactDetails.pure[WebMonad]

      businessAddress <- if (change.contains(ContactAddress)) {
        ask(addressMapping, "business-address", default = data.updatedBusinessAddress)(implicitly, implicitly, extraMessages, implicitly)
      } else data.updatedBusinessAddress.pure[WebMonad]

    } yield data.copy (
      updatedBusinessAddress = businessAddress,
      updatedProductionSites = packSites,
      updatedWarehouseSites  = warehouses,
      updatedContactDetails  = contact
    )
  }

  private def activityUpdate(
    data: RegistrationVariationData,
    subscription: RetrievedSubscription,
    returnPeriods: List[ReturnPeriod]
  )(implicit request: Request[_]): WebMonad[RegistrationVariationData] = {

    for {
      packLarge                   <- askOneOf("amount-produced", ProducerType.values.toList) map {
                                        case Large => Some(true)
                                        case Small => Some(false)
                                        case _ => None
                                      }
      useCopacker                 <- ask(bool("third-party-packagers"),"third-party-packagers", data.usesCopacker) when packLarge.contains(false)
      packageOwn                  <- askOption(litreagePair.nonEmpty, "packaging-site")(approxLitreageForm, implicitly, implicitly, implicitly) when packLarge.nonEmpty
      copacks                     <- askOption(litreagePair.nonEmpty, "contract-packing")(approxLitreageForm, implicitly, implicitly, implicitly)
      imports                     <- askOption(litreagePair.nonEmpty, "imports")(approxLitreageForm, implicitly, implicitly, implicitly)
      noUkActivity                =  (copacks, imports).isEmpty
      smallProducerWithNoCopacker =  packLarge.forall(_ == false) && useCopacker.forall(_ == false)
      shouldDereg                 =  noUkActivity && smallProducerWithNoCopacker
      packer                      =  (packLarge.contains(true) && packageOwn.flatten.nonEmpty) || !copacks.isEmpty
      isVoluntary                 =  subscription.activity.voluntaryRegistration
      variation                   <- if (shouldDereg && (returnPeriods.isEmpty || isVoluntary))
                                       tell("suggest-deregistration", uniform.confirmOrGoBackTo("suggest-deregistration", "amount-produced")) >> deregisterUpdate(data)
                                    else if (shouldDereg && (returnPeriods.nonEmpty || !isVoluntary))
                                      fileReturnsBeforeDereg(returnPeriods, data)
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
                                          packageOwn = packageOwn.map {x => x.isDefined},
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

      deregDate <- ask(cancelRegDate
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
      _ <- clear
      _ <- resultToWebMonad[A](Redirect(routes.ServicePageController.show()))
    } yield data

  private[controllers] def program(
    subscription: RetrievedSubscription,
    sdilRef: String
  )(implicit hc: HeaderCarrier, request: Request[_]): WebMonad[Result] = {

    val base = RegistrationVariationData(subscription)

    for {
      variableReturns <- execute(sdilConnector.returns_variable(base.original.utr))
      returnPeriods <- execute(sdilConnector.returns_pending(subscription.utr))
      x <- programInner(subscription, sdilRef, variableReturns, returnPeriods)
    } yield (x)
  }

  // this has been separated out from program to facilitate unit testing -
  // once the stubbing has been fixed the change here can be reverted
  private[controllers] def programInner(
    subscription: RetrievedSubscription,
    sdilRef: String,
    variableReturns: List[ReturnPeriod],
    returnPeriods: List[ReturnPeriod]
  )(implicit hc: HeaderCarrier, request :Request[_]): WebMonad[Result] = {
    val base = RegistrationVariationData(subscription)

    val onlyReturns = subscription.deregDate.nonEmpty
    val changeTypes = ChangeType.values.toList.filter(x => variableReturns.nonEmpty || x != ChangeType.Returns)
    val isVoluntary = subscription.activity.voluntaryRegistration


    for {
      changeType <- askOneOf("select-change", changeTypes, helpText = Html(Messages("change.latency")).some) when !onlyReturns
      variation <- changeType match {
        case None | Some(ChangeType.Returns) =>
          chooseReturn(subscription, sdilRef)
        case Some(ChangeType.Sites) => contactUpdate(base)
        case Some(ChangeType.Activity) => activityUpdate(base, subscription, returnPeriods)
        case Some(ChangeType.Deregister) if returnPeriods.isEmpty || isVoluntary =>
          deregisterUpdate(base)
        case Some(ChangeType.Deregister) =>
          fileReturnsBeforeDereg(returnPeriods, base)
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
      whnKey = variation match {
        case a if a.manToVol => "manToVol".some
        case a if a.volToMan => "volToMan".some
        case _ => None
      }
      whn = uniform.fragments.variationsWHN(
        path,
        newPackagingSites(variation),
        closedPackagingSites(variation),
        newWarehouseSites(variation),
        closedWarehouseSites(variation),
        variation.some,
        None,
        whnKey)
      exit <- journeyEnd(
        id = "variationDone",
        subheading = subheading,
        whatHappensNext = whn.some)(extraMessages)
    } yield exit
  }

  private def chooseReturn[A](
    subscription: RetrievedSubscription,
    sdilRef: String
  )(implicit hc: HeaderCarrier, request: Request[_]): WebMonad[A] = {
    val base = RegistrationVariationData(subscription)
    for {
      variableReturns <- execute(sdilConnector.returns_variable(base.original.utr))
      messages = variableReturns.map { x =>
        s"select-return.option.${x.year}${x.quarter}" -> s"${Messages(s"select-return.option.${x.quarter}")} ${x.year}"
      }.toMap ++ Map("error.radio-form.choose-option" -> "error.radio-form.choose-option.returnPeriod")

      extraMessages = ExtraMessages(messages)

      returnPeriod <- askOneOf("select-return", variableReturns.sortWith(_>_).map(x => s"${x.year}${x.quarter}"))(extraMessages)
        .map(y => variableReturns.filter(x => x.quarter === y.takeRight(1).toInt && x.year === y.init.toInt).head)
      _ <- clear
      _ <- resultToWebMonad[A](Redirect(routes.VariationsController.adjustment(year = returnPeriod.year, quarter = returnPeriod.quarter, id = "return-details")))
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

      origReturn <- execute(connector.returns_get(base.original.utr, returnPeriod))
        .map(_.getOrElse(throw new NotFoundException(s"No return for ${returnPeriod.year} quarter ${returnPeriod.quarter}")))

      newReturn <- askReturn(base.original, sdilRef, sdilConnector, returnPeriod, origReturn.some)

      variation = ReturnVariationData(origReturn, newReturn, returnPeriod, base.original.orgName, base.original.address, "")
      path <- getPath
      broughtForward <- if(config.balanceAllEnabled)
        execute(sdilConnector.balanceHistory(sdilRef, withAssessment = false).map { x =>
          extractTotal(listItemsWithTotal(x))
        })
      else
        execute(sdilConnector.balance(sdilRef, withAssessment = false))

      extraMessages = ExtraMessages(
            messages = Map(
              "heading.return-details" -> s"${Messages(s"select-return.option.${variation.period.quarter}")} ${variation.period.year} return details",
              "return-correction-reason.label" -> s"Reason for correcting ${Messages(s"select-return.option.${variation.period.quarter}")} ${variation.period.year} return",
              "heading.check-answers.orgName" -> s"${subscription.orgName}",
              "heading.return-details.orgName" -> s"${subscription.orgName}",
              "heading.check-return-changes.orgName" -> s"${subscription.orgName}",
              "heading.repayment-method.orgName" -> s"${subscription.orgName}",
              "heading.return-correction-reason.orgName" -> s"${subscription.orgName}"
            )
      )

      isSmallProd <- execute(isSmallProducer(sdilRef, sdilConnector, returnPeriod))

      _ <- checkYourReturnAnswers("return-details", variation.revised, broughtForward, base.original, isSmallProd, originalReturn = variation.original.some)(extraMessages, implicitly)

      reason <- askBigText(
        "return-correction-reason",
        constraints = List(("error.return-correction-reason.tooLong",
          _.length <= 255)),
        errorOnEmpty = "error.return-correction-reason.empty")(extraMessages)

      repayment <- askOneOf(
        "repayment-method",
        List("credit", "bankPayment")
      )(extraMessages) when variation.revised.total - variation.original.total < 0

      _ <- checkReturnChanges("check-return-changes", variation.copy(reason = reason, repaymentMethod = repayment), broughtForward)(extraMessages)
      _ <- execute(sdilConnector.returns_vary(sdilRef, variation.copy(reason = reason, repaymentMethod = repayment)))
      _ <- clear
      subheading = uniform.fragments.return_variation_done_subheading(subscription, returnPeriod).some

      exit <- journeyEnd(
        id = "returnVariationDone",
        subheading = subheading,
        whatHappensNext = uniform.fragments.variationsWHN(
          a = variation.copy(reason = reason, repaymentMethod = repayment).some,
          key = Some("return"),
          broughtForward = broughtForward.some).some
      )(extraMessages)

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

  private[controllers] def changeBusinessAddressJourney (
    subscription: RetrievedSubscription,
    sdilRef: String
  )(implicit hc: HeaderCarrier): WebMonad[Result] = {
    val base = RegistrationVariationData(subscription)

    for {
      _ <- changeBusinessAddressTemplate("change-registered-account-details", subscription)
      variation <- contactUpdate(base)
      path <- getPath
      _ <- checkYourRegAnswers("checkyouranswers", variation, path)
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
      exit <- journeyEnd(
        id = "variationDone",
        subheading = subheading,
        whatHappensNext = uniform.fragments.variationsWHN(
          path,
          newPackagingSites(variation),
          closedPackagingSites(variation),
          newWarehouseSites(variation),
          closedWarehouseSites(variation),
          variation.some).some)(extraMessages)
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
  )(implicit hc: HeaderCarrier, request: Request[_]): WebMonad[Result] = {
    val base = RegistrationVariationData(subscription)

    for {
      returnPeriods <- execute(sdilConnector.returns_pending(subscription.utr))
      variation <- activityUpdate(base, subscription, returnPeriods)

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
