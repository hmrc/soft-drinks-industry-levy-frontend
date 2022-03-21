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
import play.api.mvc.Request
import play.twirl.api.Html
import sdil.config.AppConfig
import sdil.connectors.SoftDrinksIndustryLevyConnector
import sdil.controllers.{Subset, askEmptyOption, longTupToLitreage}
import sdil.models.backend.{Site, UkAddress}
import sdil.models.retrieved.RetrievedSubscription
import sdil.models.variations.{Convert, RegistrationVariationData, ReturnVariationData}
import sdil.models.{Address, CYA, ContactDetails, Producer, ReturnPeriod, ReturnsVariation, SdilReturn, Warehouse, extractTotal, listItemsWithTotal}
import sdil.uniform.SdilComponents.ProducerType
import uk.gov.hmrc.http.HeaderCarrier
import views.html.uniform
import views.html.uniform.helpers.dereg_variations_cya

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

object VariationsJourney {

  sealed trait AbstractChangeType extends EnumEntry

  sealed trait ChangeType extends AbstractChangeType
  object ChangeType extends Enum[ChangeType] {
    val values = findValues

    case object AASites extends ChangeType
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

  def message(key: String, args: String*) = {
    import play.twirl.api.HtmlFormat.escape
    Map(key -> Tuple2(key, args.toList.map { escape(_).toString }))
  }

  import cats.Order
  implicit val ldOrder: Order[LocalDate] = Order.by(_.toEpochDay)

  def closedWarehouseSites(variation: VariationsJourney.Change.RegChange): List[Site] = {
    val old = variation.data.original.warehouseSites
      .filter { x =>
        x.closureDate.fold(true) { _.isAfter(LocalDate.now) }
      }
      .map(x => x.address)

    val newList = variation.data.updatedWarehouseSites
      .filter { x =>
        x.closureDate.fold(true) { _.isAfter(LocalDate.now) }
      }
      .map(x => x.address)

    val diff = old diff newList

    variation.data.updatedWarehouseSites.filter { site =>
      diff.exists { address =>
        compareAddress(site.address, address)
      }
    }
  }.toList

  def closedPackagingSites(variation: VariationsJourney.Change.RegChange): List[UkAddress] = {
    val old = variation.data.original.productionSites.map(x => x.address)
    val newlist = variation.data.updatedProductionSites.map(x => x.address)
    val diff = old diff newlist
    diff
  }

  def newPackagingSites(variation: VariationsJourney.Change.RegChange): List[UkAddress] = {
    val old = variation.data.original.productionSites.map(x => x.address)
    val updated = variation.data.updatedProductionSites.map(x => x.address) //This contains all the packaging sites new and old
    val matching = updated diff (old)
    val finals = updated.filter(_ != matching)
    println(s"matching = $matching")
    println(s"FINAL matching = $finals")
    matching.toList
  }

  def newWarehouseSites(variation: VariationsJourney.Change.RegChange): List[Site] = {
    val oldAddress = variation.data.original.warehouseSites.map(x => x.address)
    val updatedAddress = variation.data.updatedWarehouseSites.map(x => x.address) //This contains all the packaging sites new and old
    val newAddress = updatedAddress diff oldAddress

    variation.data.updatedWarehouseSites.filter { site =>
      newAddress.exists { address =>
        compareAddress(site.address, address)
      }
    }
  }.toList

  def compareAddress(a1: UkAddress, a2: UkAddress): Boolean =
    a1.postCode.equals(a2.postCode) && a1.lines.equals(a2.lines)

  def closedSites(sites: List[Site], closedSites: List[String]): List[Site] =
    sites
      .filter { x =>
        x.closureDate.fold(true) {
          _.isAfter(LocalDate.now)
        }
      }
      .filter(x => closedSites.contains(x.ref.getOrElse("")))

  def changeBusinessAddressJourney(
    id: String,
    subscription: RetrievedSubscription,
    ufViews: views.uniform.Uniform
  )(implicit request: Request[_]) = {

    val base = RegistrationVariationData(subscription)

    for {
      _ <- tell(
            key = "change-registered-account-details",
            value = ufViews.updateBusinessAddresses(id, subscription, Address.fromUkAddress(subscription.address))(
              implicitly,
              _: Messages),
            customContent = message("change-registered-account-details.caption", subscription.orgName)
          )
      variation <- contactUpdate(base)
      _ <- tell(
            "check-answers",
            views.html.uniform.fragments.variations_cya(
              variation.data,
              newPackagingSites(variation),
              closedPackagingSites(variation),
              newWarehouseSites(variation),
              closedWarehouseSites(variation),
              List("contact-details")
            )(_: Messages)
          )

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
    val changeOptions =
      if (data.isVoluntary)
        ContactChangeType.values.filter(_ != ContactChangeType.Sites)
      else
        ContactChangeType.values

    for {
      change <- ask[Set[ContactChangeType]](
                 "change-registered-details",
                 validation = Rule.nonEmpty[Set[ContactChangeType]] alongWith Subset(changeOptions: _*)
               )

      packSites <- askList[Address](
                    "packaging-site-details",
                    data.updatedProductionSites.toList.map(x => Address.fromUkAddress(x.address)).some,
                    Rule.nonEmpty[List[Address]]
                  )(
                    {
                      case (index: Option[Int], existingAddresses: List[Address]) =>
                        ask[Address](
                          "p-site",
                          default = index.map(existingAddresses)
                        )
                    }, {
                      case (index: Int, existingAddresses: List[Address]) =>
                        interact[Boolean]("remove-packaging-site-details", existingAddresses(index).nonEmptyLines)
                    }
                  ).map(_.map(Site.fromAddress)) emptyUnless (
                    (data.producer.isLarge.contains(true) || data.copackForOthers) && change.contains(Sites)
                  )

      warehouses <- askList[Warehouse](
                     "warehouse-details",
                     data.updatedWarehouseSites.toList.map(Warehouse.fromSite).some
                   )(
                     {
                       case (index: Option[Int], existingWarehouses: List[Warehouse]) =>
                         ask[Warehouse]("w-house", default = index.map(existingWarehouses))
                     }, {
                       case (index: Int, existingWarehouses: List[Warehouse]) =>
                         interact[Boolean]("remove-warehouse-details", existingWarehouses(index).nonEmptyLines)
                     }
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

  def changeActor(
    data: RegistrationVariationData,
    subscription: RetrievedSubscription,
    returnPeriods: List[ReturnPeriod]
  )(implicit ec: ExecutionContext, ufMessages: UniformMessages[Html]) =
    for {
      producerType <- ask[ProducerType]("amount-produced")
      useCopacker  <- if (producerType == ProducerType.Small) ask[Boolean]("third-party-packagers") else pure(false)
      packageOwn   <- askEmptyOption[(Long, Long)]("packaging-site") when producerType != ProducerType.XNot
      copacks      <- askEmptyOption[(Long, Long)]("contract-packing")
      imports      <- askEmptyOption[(Long, Long)]("imports")
      noUkActivity = (copacks._1 + copacks._2 + imports._1 + imports._2) == 0
      smallProducerWithNoCopacker = producerType != ProducerType.Large && !useCopacker
      shouldDereg = noUkActivity && smallProducerWithNoCopacker
      packer = (producerType == ProducerType.Large && (packageOwn.get._1 + packageOwn.get._2) > 0) || (copacks._1 > 0L || copacks._2 > 0L)
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
                      end(
                        "file-return-before-deregistration",
                        uniform.fragments.return_before_dereg("file-return-before-deregistration", returnPeriods)
                      )
                    }
                  } else {
                    val warehouseShow = producerType == ProducerType.Small && useCopacker && (copacks._1 + copacks._2 + imports._1 + imports._2) == 0
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
                      packSites <- askList[Address](
                                    "production-site-details",
                                    data.updatedProductionSites.toList.map(x => Address.fromUkAddress(x.address)).some,
                                    Rule.nonEmpty[List[Address]]
                                  )(
                                    {
                                      case (index: Option[Int], existingAddresses: List[Address]) =>
                                        ask[Address]("p-site", default = index.map(existingAddresses))
                                    }, {
                                      case (index: Int, existingAddresses: List[Address]) =>
                                        interact[Boolean](
                                          "remove-production-site-details",
                                          existingAddresses(index).nonEmptyLines)
                                    }
                                  ).map(_.map(Site.fromAddress)) emptyUnless packer

                      warehouses <- askList[Warehouse](
                                     "secondary-warehouse-details",
                                     data.updatedWarehouseSites.toList
                                       .map(x =>
                                         Warehouse.fromSite(Site(x.address, x.ref, x.tradingName, x.closureDate)))
                                       .some
                                   )(
                                     {
                                       case (index: Option[Int], existingWarehouses: List[Warehouse]) =>
                                         ask[Warehouse]("w-house", default = index.map(existingWarehouses))
                                     }, {
                                       case (index: Int, existingWarehouses: List[Warehouse]) =>
                                         interact[Boolean](
                                           "remove-warehouse-details",
                                           existingWarehouses(index).nonEmptyLines)
                                     }
                                   ).map(_.map(Site.fromWarehouse)).emptyUnless(!warehouseShow)
                    } yield
                      Change.RegChange(
                        data.copy(
                          producer =
                            Producer(producerType != ProducerType.XNot, (producerType == ProducerType.Large).some),
                          usesCopacker = useCopacker.some,
                          packageOwn =
                            if (packageOwn
                                  .getOrElse(0) == 0) { Some(false) } else if ((packageOwn.get._1 + packageOwn.get._2) > 0) {
                              Some(true)
                            } else { Some(false) },
                          packageOwnVol = longTupToLitreage(packageOwn),
                          copackForOthers = if ((copacks._1 + copacks._2) == 0) { false } else { true },
                          copackForOthersVol = longTupToLitreage(copacks),
                          imports = if ((imports._1 + imports._2) == 0) { false } else { true },
                          importsVol = longTupToLitreage(imports),
                          updatedProductionSites = packSites,
                          updatedWarehouseSites = warehouses
                        ))
                  }
    } yield variation

  def changeActorJourney(
    id: String,
    subscription: RetrievedSubscription,
    sdilRef: String,
    variableReturns: List[ReturnPeriod],
    pendingReturns: List[ReturnPeriod],
    sdilConnector: SoftDrinksIndustryLevyConnector,
    checkSmallProducerStatus: (String, ReturnPeriod) => Future[Option[Boolean]],
    getReturn: ReturnPeriod => Future[Option[SdilReturn]],
    submitReturnVariation: ReturnsVariation => Future[Unit],
    appConfig: AppConfig,
  )(implicit ec: ExecutionContext, hc: HeaderCarrier, ufMessages: UniformMessages[Html]) = {
    val base = RegistrationVariationData(subscription)
    val isVoluntary = subscription.activity.voluntaryRegistration
    for {
      variation <- changeActor(base, subscription, pendingReturns)
   //   _         <- tell("check-answers", CYA(variation))
    } yield variation.data
  }

  def activityUpdateJourney(
    data: RegistrationVariationData,
    subscription: RetrievedSubscription,
    returnPeriods: List[ReturnPeriod]
  )(implicit ec: ExecutionContext, ufMessages: UniformMessages[Html]) =
    for {
      producerType <- ask[ProducerType]("amount-produced")
      useCopacker  <- if (producerType == ProducerType.Small) ask[Boolean]("third-party-packagers") else pure(false)
      packageOwn   <- askEmptyOption[(Long, Long)]("packaging-site") when producerType != ProducerType.XNot
      copacks      <- askEmptyOption[(Long, Long)]("contract-packing")
      imports      <- askEmptyOption[(Long, Long)]("imports")
      noUkActivity = (copacks._1 + copacks._2 + imports._1 + imports._2) == 0
      smallProducerWithNoCopacker = producerType != ProducerType.Large && !useCopacker
      shouldDereg = noUkActivity && smallProducerWithNoCopacker
      packer = (producerType == ProducerType.Large && (packageOwn.get._1 + packageOwn.get._2) > 0) || (copacks._1 > 0L || copacks._2 > 0L)
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
                      end(
                        "file-return-before-deregistration",
                        uniform.fragments.return_before_dereg("file-return-before-deregistration", returnPeriods)
                      )
                    }
                  } else {
                    val warehouseShow = producerType == ProducerType.Small && useCopacker && (copacks._1 + copacks._2 + imports._1 + imports._2) == 0
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
                      packSites <- askList[Address](
                                    "production-site-details",
                                    data.updatedProductionSites.toList.map(x => Address.fromUkAddress(x.address)).some,
                                    Rule.nonEmpty[List[Address]]
                                  )(
                                    {
                                      case (index: Option[Int], existingAddresses: List[Address]) =>
                                        ask[Address]("p-site", default = index.map(existingAddresses))
                                    }, {
                                      case (index: Int, existingAddresses: List[Address]) =>
                                        interact[Boolean](
                                          "remove-production-site-details",
                                          existingAddresses(index).nonEmptyLines)
                                    }
                                  ).map(_.map(Site.fromAddress)) emptyUnless packer

                      warehouses <- askList[Warehouse](
                                     "secondary-warehouse-details",
                                     data.updatedWarehouseSites.toList
                                       .map(x =>
                                         Warehouse.fromSite(Site(x.address, x.ref, x.tradingName, x.closureDate)))
                                       .some
                                   )(
                                     {
                                       case (index: Option[Int], existingWarehouses: List[Warehouse]) =>
                                         ask[Warehouse]("w-house", default = index.map(existingWarehouses))
                                     }, {
                                       case (index: Int, existingWarehouses: List[Warehouse]) =>
                                         interact[Boolean](
                                           "remove-warehouse-details",
                                           existingWarehouses(index).nonEmptyLines)
                                     }
                                   ).map(_.map(Site.fromWarehouse)).emptyUnless(!warehouseShow)
                    } yield
                      Change.RegChange(
                        data.copy(
                          producer =
                            Producer(producerType != ProducerType.XNot, (producerType == ProducerType.Large).some),
                          usesCopacker = useCopacker.some,
                          packageOwn =
                            if (packageOwn
                                  .getOrElse(0) == 0) { Some(false) } else if ((packageOwn.get._1 + packageOwn.get._2) > 0) {
                              Some(true)
                            } else { Some(false) },
                          packageOwnVol = longTupToLitreage(packageOwn),
                          copackForOthers = if ((copacks._1 + copacks._2) == 0) { false } else { true },
                          copackForOthersVol = longTupToLitreage(copacks),
                          imports = if ((imports._1 + imports._2) == 0) { false } else { true },
                          importsVol = longTupToLitreage(imports),
                          updatedProductionSites = packSites,
                          updatedWarehouseSites = warehouses
                        ))
                  }
    } yield variation

  private def adjust(
    id: String,
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
                    id,
                    returnPeriod,
                    origReturn,
                    subscription,
                    checkSmallProducerStatus,
                    submitReturnVariation,
                    broughtForward,
                    isSmallProd.getOrElse(false),
                  )
      emptyReturn = SdilReturn((0, 0), (0, 0), Nil, (0, 0), (0, 0), (0, 0), (0, 0), None)
      variation = ReturnVariationData(
        origReturn.getOrElse(emptyReturn),
        newReturn._1,
        returnPeriod,
        base.original.orgName,
        base.original.address,
        "")

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
    id: String,
    subscription: RetrievedSubscription,
    sdilRef: String,
    variableReturns: List[ReturnPeriod],
    pendingReturns: List[ReturnPeriod],
    sdilConnector: SoftDrinksIndustryLevyConnector,
    checkSmallProducerStatus: (String, ReturnPeriod) => Future[Option[Boolean]],
    getReturn: ReturnPeriod => Future[Option[SdilReturn]],
    submitReturnVariation: ReturnsVariation => Future[Unit],
    appConfig: AppConfig,
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
                        adjustedReturn <- adjust(
                                           id,
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

                    case ChangeType.AASites =>
                      for {
                        contacts <- contactUpdate(base)
                      } yield { contacts: Change }

                    case ChangeType.Deregister if pendingReturns.isEmpty || isVoluntary =>
                      for {
                        dereg <- deregisterUpdate(base)
                      } yield { dereg: Change }

                    case ChangeType.Deregister =>
                      end(
                        "file-return-before-deregistration",
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
