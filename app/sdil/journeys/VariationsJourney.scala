/*
 * Copyright 2022 HM Revenue & Customs
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

package sdil.journeys

import cats.implicits._
import enumeratum.{Enum, EnumEntry}
import ltbs.uniform._
import ltbs.uniform.validation.{Rule, _}
import play.api.i18n.Messages
import play.twirl.api.Html
import sdil.config.AppConfig
import sdil.connectors.SoftDrinksIndustryLevyConnector
import sdil.controllers.{ShowBackLink, Subset, askEmptyOption, askListSimple, longTupToLitreage}
import sdil.journeys.VariationsJourney.RepaymentMethod.Credit
import sdil.models.backend.Site
import sdil.models.retrieved.RetrievedSubscription
import sdil.models.variations.{Convert, RegistrationVariationData, ReturnVariationData}
import sdil.models.{Address, CYA, ContactDetails, Producer, ReturnPeriod, ReturnsVariation, SdilReturn, Warehouse, extractTotal, listItemsWithTotal}
import sdil.uniform.ProducerType
import uk.gov.hmrc.http.HeaderCarrier
import views.html.uniform
import views.html.uniform.helpers.dereg_variations_cya

import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatter.ofPattern
import java.time.{LocalDate, LocalTime, ZoneId}
import scala.concurrent.{ExecutionContext, Future}

object VariationsJourney {

  sealed trait AbstractChangeType extends EnumEntry

  sealed trait ChangeType extends AbstractChangeType
  object ChangeType extends Enum[ChangeType] {
    val values = findValues

    case object Sites extends ChangeType
    case object Activity extends ChangeType
    case object Deregister extends ChangeType
  }
  sealed trait ChangeTypeWithReturns extends AbstractChangeType
  object ChangeTypeWithReturns extends Enum[ChangeTypeWithReturns] {
    val values = findValues
    case object Returns extends ChangeTypeWithReturns
  }

  sealed trait Change
  object Change {
    case class RegChange(data: RegistrationVariationData) extends Change
    case class Returns(data: ReturnVariationData) extends Change
  }

  sealed trait ContactChangeType extends EnumEntry
  object ContactChangeType extends Enum[ContactChangeType] {
    val values = findValues
    case object Sites extends ContactChangeType
    case object ContactPerson extends ContactChangeType
    case object ContactAddress extends ContactChangeType
  }

  sealed trait RepaymentMethod extends EnumEntry
  object RepaymentMethod extends Enum[RepaymentMethod] {
    val values = findValues
    case object Credit extends RepaymentMethod
    case object BankPayment extends RepaymentMethod
  }

  import cats.Order
  implicit val ldOrder: Order[LocalDate] = Order.by(_.toEpochDay)

  def changeBusinessAddressJourney(
    subscription: RetrievedSubscription,
    sdilRef: String
  ) = {

    def htmlMessage: Html =
      Html(
        "TODO - on the old version the content is constructed from update_business_addresses.scala.html, " +
          "but this also contains the form, button, etc which is unneeded with the new version")

    val base = RegistrationVariationData(subscription)

    for {
      _         <- tell("change-business-address", htmlMessage)
      variation <- contactUpdate(base)
      _         <- tell("check-answers", Html(s"$variation"))
    } yield (variation)

  }

  private def deregisterUpdate(
    data: RegistrationVariationData
  ) =
    for {
      reason <- ask[String](
                 "cancel-registration-reason",
                 validation = Rule.nonEmpty[String] followedBy Rule.maxLength[String](255)
               )
      deregDate <- ask[LocalDate](
                    "cancel-registration-date",
                    validation = Rule.between[LocalDate](LocalDate.now, LocalDate.now.plusDays(15))
                  )
    } yield
      Change.RegChange(
        data.copy(
          reason = reason.some,
          deregDate = deregDate.some
        )
      )

  private def contactUpdate(
    data: RegistrationVariationData
  ) = {

    import ContactChangeType._

    // which things are they allowed to change?
    val changeOptions =
      if (data.isVoluntary)
        ContactChangeType.values.filter(_ != ContactChangeType.Sites)
      else
        ContactChangeType.values

    for {

      // give them a multiple-choice list of things to change
      change <- ask[Set[ContactChangeType]](
                 "change-registered-details",
                 validation = Rule.nonEmpty[Set[ContactChangeType]] alongWith Subset(changeOptions: _*)
               )

      packSites <- askListSimple[Address](
                    "production-site-details",
                    "p-site",
                    listValidation = Rule.nonEmpty[List[Address]],
                    default = data.updatedProductionSites.toList.map(x => Address.fromUkAddress(x.address)).some
                  ).map(_.map(Site.fromAddress)) emptyUnless (
                    (data.producer.isLarge.contains(true) || data.copackForOthers) && change.contains(Sites)
                  )

      warehouses <- askListSimple[Warehouse](
                     "warehouses",
                     "w-house",
                     default = data.updatedWarehouseSites.toList.map(Warehouse.fromSite).some
                   ).map(_.map(Site.fromWarehouse)) emptyUnless change.contains(Sites)

      contact <- if (change.contains(ContactPerson)) {
                  ask[ContactDetails]("contact-details", default = data.updatedContactDetails.some)
                } else pure(data.updatedContactDetails)

      businessAddress <- if (change.contains(ContactAddress)) {
                          ask[Address]("business-address", default = data.updatedBusinessAddress.some)
                        } else pure(data.updatedBusinessAddress)

    } yield
      Change.RegChange(
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
      producerType <- ask[ProducerType]("amount-produced")
      useCopacker  <- if (producerType == ProducerType.Small) ask[Boolean]("third-party-packagers") else pure(false)
      packageOwn   <- askEmptyOption[(Long, Long)]("packaging-site")
      copacks      <- askEmptyOption[(Long, Long)]("contract-packing")
      imports      <- askEmptyOption[(Long, Long)]("imports")
      noUkActivity = (copacks, imports).isEmpty
      smallProducerWithNoCopacker = producerType != ProducerType.Large && !useCopacker
      shouldDereg = noUkActivity && smallProducerWithNoCopacker
      packer = (producerType == ProducerType.Large && packageOwn.nonEmpty) || copacks.nonEmpty
      isVoluntary = subscription.activity.voluntaryRegistration

      variation <- if (shouldDereg) {
                    if (returnPeriods.isEmpty || isVoluntary) {
                      val deregHtml =
                        uniform.confirmOrGoBackTo("suggest-deregistration", "amount-produced")(_: Messages)
                      for {
                        _              <- tell("suggest-deregistration", deregHtml)
                        deregVariation <- deregisterUpdate(data)
                        _              <- tell("check-answers", dereg_variations_cya(showChangeLinks = true, data)(_: Messages))
                      } yield deregVariation
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
                        List(Address.fromUkAddress(data.original.address))
                      } else {
                        data.updatedProductionSites.toList.map(x => Address.fromUkAddress(x.address))
                      }
                      packSites <- askListSimple[Address](
                                    "production-site-details",
                                    "p-site",
                                    default = pSites.some,
                                    listValidation = Rule.nonEmpty[List[Address]]
                                  ).map(_.map(Site.fromAddress)) emptyUnless packer
                      isVoluntary = producerType == ProducerType.Small && useCopacker && (copacks, imports).isEmpty
                      warehouses <- askListSimple[Warehouse](
                                     "warehouses",
                                     "w-house",
                                     default = data.updatedWarehouseSites.toList.map(Warehouse.fromSite).some
                                   ).map(_.map(Site.fromWarehouse)) emptyUnless !isVoluntary
                    } yield
                      Change.RegChange(
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
    checkSmallProducerStatus: (String, ReturnPeriod) => Future[Option[Boolean]],
    getReturn: ReturnPeriod => Future[Option[SdilReturn]],
    submitReturnVariation: ReturnsVariation => Future[Unit],
    config: AppConfig
  )(implicit ec: ExecutionContext, hc: HeaderCarrier) = {
    val base = RegistrationVariationData(subscription)
    implicit val showBackLink: ShowBackLink = ShowBackLink(false)
    for {
      isSmallProd <- convertWithKey("is-small-producer")(checkSmallProducerStatus(sdilRef, returnPeriod))
      origReturn  <- convertWithKey("return-lookup")(getReturn(returnPeriod))
      broughtForward <- if (config.balanceAllEnabled) {
                         convertWithKey("balance-history") {
                           connector.balanceHistory(sdilRef, withAssessment = false).map { x =>
                             extractTotal(listItemsWithTotal(x))
                           }
                         }
                       } else {
                         convertWithKey("balance")(connector.balance(sdilRef, withAssessment = false))
                       }
      newReturn <- ReturnsJourney.journey(
                    returnPeriod,
                    origReturn,
                    subscription,
                    checkSmallProducerStatus,
                    submitReturnVariation,
                    broughtForward,
                    isSmallProd.getOrElse(false),
                  ) // , base.original, sdilRef, sdilConnector, returnPeriod, origReturn.some)
      emptyReturn = SdilReturn((0, 0), (0, 0), Nil, (0, 0), (0, 0), (0, 0), (0, 0), None)
      variation = ReturnVariationData(
        origReturn.getOrElse(emptyReturn),
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

      reason <- ask[String](
                 "return-correction-reason",
                 validation = Rule.nonEmpty[String]("required") followedBy Rule.maxLength(255))
      repayment <- ask[RepaymentMethod]("repayment-method") when
                    (variation.revised.total - variation.original.total < 0)
      repaymentString = repayment match {
        case Some(RepaymentMethod.Credit)      => "credit".some
        case Some(RepaymentMethod.BankPayment) => "bankPayment".some
        case None                              => None
      }
      payMethodAndReason = variation.copy(reason = reason, repaymentMethod = repaymentString)
      _ <- tell(
            "check-return-changes",
            uniform.fragments.returnVariationDifferences(
              "check-return-changes",
              payMethodAndReason,
              showChangeLinks = true,
              broughtForward.some
            )(_: Messages)
          )

    } yield Change.Returns(payMethodAndReason)
  }

  def journey(
    subscription: RetrievedSubscription,
    sdilRef: String,
    variableReturns: List[ReturnPeriod],
    pendingReturns: List[ReturnPeriod],
    sdilConnector: SoftDrinksIndustryLevyConnector,
    checkSmallProducerStatus: (String, ReturnPeriod) => Future[Option[Boolean]],
    getReturn: ReturnPeriod => Future[Option[SdilReturn]],
    submitReturnVariation: ReturnsVariation => Future[Unit],
    appConfig: AppConfig
  )(implicit ec: ExecutionContext, hc: HeaderCarrier, ufMessages: UniformMessages[Html]) = {
    val base = RegistrationVariationData(subscription)
    val isVoluntary = subscription.activity.voluntaryRegistration

    for {
      changeType <- if (variableReturns.nonEmpty) {
                     ask[AbstractChangeType]("select-change")
                   } else ask[ChangeType]("select-change")
      variation <- changeType match {
                    case ChangeTypeWithReturns.Returns =>
                      for {
                        period <- ask[ReturnPeriod](
                                   "select-return",
                                   validation = Rule.in(variableReturns)
                                 )
                        ///  original journey redirected from here to adjust()
                        adjustedReturn <- adjust(
                                           subscription,
                                           sdilRef,
                                           sdilConnector,
                                           period,
                                           checkSmallProducerStatus,
                                           getReturn,
                                           submitReturnVariation,
                                           appConfig)
                      } yield { adjustedReturn: Change }

                    case ChangeType.Activity =>
                      activityUpdateJourney(base, subscription, pendingReturns)

                    case ChangeType.Sites =>
                      for {
                        contacts <- contactUpdate(base)
                      } yield { contacts: Change }

                    case ChangeType.Deregister if pendingReturns.isEmpty || isVoluntary =>
                      for {
                        dereg <- deregisterUpdate(base)
                      } yield { dereg: Change }

                    case ChangeType.Deregister =>
                      end(
                        "file-returns-before-dereg",
                        uniform.fragments.return_before_dereg("file-return-before-deregistration", pendingReturns)
                      )
                  }

      _ <- tell("check-answers", CYA(variation)) when (changeType != ChangeTypeWithReturns.Returns)

    } yield {
      variation match {
        case Change.RegChange(regChange) => Right(regChange)
        case Change.Returns(retChange)   => Left(retChange)

      }
    }
  }
}
