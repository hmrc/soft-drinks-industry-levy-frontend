/*
 * Copyright 2021 HM Revenue & Customs
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

import scala.language.higherKinds

import cats.implicits._
import enumeratum._
import java.time.LocalDate
import ltbs.play.scaffold.SdilComponents, SdilComponents.ProducerType
import ltbs.uniform._, validation._
import play.api.Logger
import play.api.i18n.I18nSupport
import play.api.mvc._
import play.twirl.api.Html
import scala.concurrent._
import sdil.actions._
import sdil.config._
import sdil.connectors.SoftDrinksIndustryLevyConnector
import sdil.models._
import sdil.models.backend.Site
import sdil.models.retrieved.RetrievedSubscription
import sdil.models.variations._
import sdil.uniform.SaveForLaterPersistenceNew
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

class VariationsControllerNew(
  mcc: MessagesControllerComponents,
  val config: AppConfig,
  val ufViews: views.uniform.Uniform,
  registeredAction: RegisteredAction,
  sdilConnector: SoftDrinksIndustryLevyConnector,
  cache: RegistrationFormDataCache
)(implicit ec: ExecutionContext)
    extends FrontendController(mcc) with I18nSupport with HmrcPlayInterpreter {

  import VariationsControllerNew._

  override def defaultBackLink = "/soft-drinks-industry-levy"

  implicit lazy val persistence =
    SaveForLaterPersistenceNew[RegisteredRequest[AnyContent]](_.sdilEnrolment.value)("variations", cache.shortLiveCache)

  val logger: Logger = Logger(this.getClass())

  def index(id: String): Action[AnyContent] = registeredAction.async { implicit request =>
    val sdilRef = request.sdilEnrolment.value
    sdilConnector.retrieveSubscription(sdilRef) flatMap {
      case Some(subscription) =>
        val base = RegistrationVariationData(subscription)
        for {
          variableReturns <- sdilConnector.returns_variable(base.original.utr)
          returnPeriods   <- sdilConnector.returns_pending(subscription.utr)
          response <- interpret(VariationsControllerNew.journey(subscription, sdilRef, variableReturns, returnPeriods))
                       .run(id) { output =>
                         Future.successful(Ok("!"))
                       }
        } yield response
      case None => Future.successful(NotFound(""))
    }
  }
}

object VariationsControllerNew {

  sealed trait ChangeType extends EnumEntry
  object ChangeType extends Enum[ChangeType] {
    val values = findValues
    case object Returns extends ChangeType
    case object Sites extends ChangeType
    case object Activity extends ChangeType
    case object Deregister extends ChangeType
  }

  sealed trait Change
  object Change {
    case class Returns(data: ReturnVariationData) extends Change
    case class Contacts(data: RegistrationVariationData) extends Change
    case class Activity(data: RegistrationVariationData) extends Change
    case class Deregister(data: RegistrationVariationData) extends Change
  }

  import cats.Order
  implicit val ldOrder: Order[LocalDate] = Order.by(_.toEpochDay)

  private def deregisterUpdate(
    data: RegistrationVariationData
  ) =
    for {
      reason <- ask[String](
                 "cancel-registration-reason",
                 validation = Rule.maxLength[String](255)
               )
      deregDate <- ask[LocalDate](
                    "cancel-registration-date",
                    validation = Rule.between[LocalDate](LocalDate.now, LocalDate.now.plusDays(15))
                  )
    } yield
      Change.Deregister(
        data.copy(
          reason = reason.some,
          deregDate = deregDate.some
        )
      )

  private def longTupToLitreage(in: (Long, Long)): Option[Litreage] =
    if (in.isEmpty) None else Litreage(in._1, in._2).some

  sealed trait ContactChangeType extends EnumEntry
  object ContactChangeType extends Enum[ContactChangeType] {
    val values = findValues
    case object Sites extends ContactChangeType
    case object ContactPerson extends ContactChangeType
    case object ContactAddress extends ContactChangeType
  }

  private def contactUpdate(
    data: RegistrationVariationData
  ) = {

    import ContactChangeType._

    for {
      change <- ask[Set[ContactChangeType]](
                 "change-registered-details",
                 validation = Rule
                   .nonEmpty[Set[ContactChangeType]] alongWith (if (data.isVoluntary) {
                                                                  Rule.in(
                                                                    ContactChangeType.values
                                                                      .filter(_ != ContactChangeType.Sites)
                                                                      .map(Set(_)))
                                                                } else Rule.alwaysPass)
               )

      packSites <- if (change.contains(Sites)) {
                    askListSimple[Site](
                      "packaging-site-details",
                      validation = Rule.nonEmpty[List[Site]],
                      default = data.updatedProductionSites.toList.some
                    ) emptyUnless (data.producer.isLarge.contains(true) || data.copackForOthers)
                  } else pure(data.updatedProductionSites.toList)

      warehouses <- if (change.contains(Sites)) {
                     askListSimple[Site]("warehouse-details", default = data.updatedWarehouseSites.toList.some)
                   } else pure(data.updatedWarehouseSites.toList)

      contact <- if (change.contains(ContactPerson)) {
                  ask[ContactDetails]("contact-details", default = data.updatedContactDetails.some)
                } else pure(data.updatedContactDetails)

      businessAddress <- if (change.contains(ContactPerson)) {
                          ask[Address]("business-address", default = data.updatedBusinessAddress.some)
                        } else pure(data.updatedBusinessAddress)

    } yield
      Change.Contacts(
        data.copy(
          updatedBusinessAddress = businessAddress,
          updatedProductionSites = packSites,
          updatedWarehouseSites = warehouses,
          updatedContactDetails = contact
        )
      )
  }

  def activityUpdateJourney(
    data: RegistrationVariationData,
    subscription: RetrievedSubscription,
    returnPeriods: List[ReturnPeriod]
  ) =
    for {
      producerType <- ask[SdilComponents.ProducerType]("amount-produced")
      useCopacker  <- if (producerType == ProducerType.Small) ask[Boolean]("third-party-packagers") else pure(false)
      packageOwn   <- askEmptyOption[(Long, Long)]("packaging-site")
      copacks      <- askEmptyOption[(Long, Long)]("contract-packing")
      imports      <- askEmptyOption[(Long, Long)]("imports")
      noUkActivity = (copacks, imports).isEmpty
      smallProducerWithNoCopacker = producerType != ProducerType.Large && useCopacker == false
      shouldDereg = noUkActivity && smallProducerWithNoCopacker
      packer = (producerType == ProducerType.Large && packageOwn.nonEmpty) || copacks.nonEmpty
      isVoluntary = subscription.activity.voluntaryRegistration
      variation <- if (shouldDereg) {
                    if (returnPeriods.isEmpty || isVoluntary) {
                      for {
                        _ <- tell("suggest-deregistration", Html("optional-back-to amount-produced"))
                        x <- deregisterUpdate(data)
                      } yield x
                    } else {
                      end("file-returns-before-dereg")
                    }
                  } else {
                    for {
                      usePPOBAddress <- (
                                         ask[Boolean]("pack-at-business-address")
                                           when packer && data.original.productionSites.isEmpty
                                       ).map(_.getOrElse(false))
                      pSites = if (usePPOBAddress) {
                        List(Site.fromAddress(Address.fromUkAddress(data.original.address)))
                      } else {
                        data.updatedProductionSites.toList
                      }
                      packSites <- askListSimple[Site]("pack-sites", validation = Rule.nonEmpty[List[Site]]) emptyUnless packer
                      isVoluntary = producerType == ProducerType.Small && useCopacker && (copacks, imports).isEmpty
                      warehouses <- askListSimple[Site]("warehouses") emptyUnless !isVoluntary
                    } yield
                      Change.Activity(
                        data.copy(
                          producer =
                            Producer(producerType != ProducerType.Not, (producerType == ProducerType.Large).some),
                          usesCopacker = useCopacker.some,
                          packageOwn = packageOwn.nonEmpty.some,
                          packageOwnVol = longTupToLitreage(packageOwn),
                          copackForOthers = copacks.nonEmpty,
                          copackForOthersVol = longTupToLitreage(copacks),
                          imports = imports.nonEmpty,
                          importsVol = longTupToLitreage(imports),
                          updatedProductionSites = packSites,
                          updatedWarehouseSites = warehouses
                        ))
                  }
    } yield variation

  private def adjust(
    subscription: RetrievedSubscription,
    sdilRef: String,
    connector: SoftDrinksIndustryLevyConnector,
    returnPeriod: ReturnPeriod,
    lookupFunction: ReturnPeriod => Future[SdilReturn],
    balanceHistoryFunction: () => Future[List[FinancialLineItem]],
    balanceFunction: () => Future[BigDecimal],
    smallProducerCheckFunction: ReturnPeriod => Future[Boolean],
    config: AppConfig
  )(implicit ec: ExecutionContext) = {
    val base = RegistrationVariationData(subscription)
    implicit val showBackLink: ShowBackLink = ShowBackLink(false)
    for {
      isSmallProd <- convertWithKey("is-small-producer")(smallProducerCheckFunction(returnPeriod))
      origReturn  <- convertWithKey("return-lookup")(lookupFunction(returnPeriod))
      broughtForward <- if (config.balanceAllEnabled) {
                         convertWithKey("balance-history") {
                           balanceHistoryFunction().map { x =>
                             extractTotal(listItemsWithTotal(x))
                           }
                         }
                       } else {
                         convertWithKey("balance")(balanceFunction())
                       }
      newReturn <- ReturnsControllerNew.journey(
                    origReturn.some,
                    subscription,
                    broughtForward,
                    isSmallProd, { _ =>
                      Future.successful(())
                    }
                  ) // , base.original, sdilRef, sdilConnector, returnPeriod, origReturn.some)

      variation = ReturnVariationData(
        origReturn,
        newReturn._1,
        returnPeriod,
        base.original.orgName,
        base.original.address,
        "")

      // extraMessages = ExtraMessages(
      //   messages = Map(
      //     "heading.return-details" -> s"${Messages(s"select-return.option.${variation.period.quarter}")} ${variation.period.year} return details",
      //     "return-correction-reason.label" -> s"Reason for correcting ${Messages(
      //       s"select-return.option.${variation.period.quarter}")} ${variation.period.year} return",
      //     "heading.check-answers.orgName"            -> s"${subscription.orgName}",
      //     "heading.return-details.orgName"           -> s"${subscription.orgName}",
      //     "heading.check-return-changes.orgName"     -> s"${subscription.orgName}",
      //     "heading.repayment-method.orgName"         -> s"${subscription.orgName}",
      //     "heading.return-correction-reason.orgName" -> s"${subscription.orgName}"
      //   )
      // )

      _ <- tell("return-details", Html("TODO-CYA"))
      // _ <- checkYourReturnAnswers(
      //       "return-details",
      //       variation.revised,
      //       broughtForward,
      //       base.original,
      //       isSmallProd,
      //       originalReturn = variation.original.some)(extraMessages, implicitly)

      reason <- ask[String]("return-correction-reason", validation = Rule.maxLength(255))
      repayment <- ask[String]("repayment-method", validation = Rule.in(List("credit", "bankPayment"))) when
                    (variation.revised.total - variation.original.total < 0)
      _ <- tell("check-return-changes", Html("TODO-CYA"))

    } yield Change.Returns(variation.copy(reason = reason, repaymentMethod = repayment))
  }

  def journey(
    subscription: RetrievedSubscription,
    sdilRef: String,
    variableReturns: List[ReturnPeriod],
    pendingReturns: List[ReturnPeriod]
  )(implicit ec: ExecutionContext) = {
    val base = RegistrationVariationData(subscription)
    val onlyReturns = subscription.deregDate.nonEmpty
    val changeTypes = ChangeType.values.toList.filter(x => variableReturns.nonEmpty || x != ChangeType.Returns)
    val isVoluntary = subscription.activity.voluntaryRegistration

    for {
      changeType <- ask[ChangeType]("select-change", validation = Rule.in(changeTypes))
      variation <- changeType match {
                    case ChangeType.Returns =>
                      for {
                        period <- ask[ReturnPeriod]("select-return", validation = Rule.in(variableReturns))
                        ///  original journey redirected from here to adjust()
                      } yield Change.Returns
                    case ChangeType.Sites                                               => contactUpdate(base)
                    case ChangeType.Activity                                            => activityUpdateJourney(base, subscription, pendingReturns)
                    case ChangeType.Deregister if pendingReturns.isEmpty || isVoluntary => deregisterUpdate(base)
                    case ChangeType.Deregister =>
                      end("file-returns-before-dereg")
                  }
    } yield variation
  }

}
