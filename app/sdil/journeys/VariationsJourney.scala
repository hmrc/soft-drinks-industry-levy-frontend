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
import ltbs.uniform.{tell, _}
import ltbs.uniform.common.web.WebTell
import ltbs.uniform.validation.{Rule, _}
import play.api.i18n.Messages
import play.api.mvc.Request
import play.twirl.api.Html
import sdil.config.AppConfig
import sdil.connectors.SoftDrinksIndustryLevyConnector
import sdil.controllers.{Subset, askEmptyOption, askListSimple, longTupToLitreage}
import sdil.journeys.ReturnsJourney.{calculateSubtotal, returnAmount}
import sdil.journeys.VariationsJourney.ContactChangeType.{ContactAddress, ContactPerson}
import sdil.models.backend.{Contact, Site, UkAddress}
import sdil.models.retrieved.RetrievedSubscription
import sdil.models.variations.{Convert, RegistrationVariationData, ReturnVariationData, VariationData}
import sdil.models.{Address, CYA, ContactDetails, Producer, ReturnPeriod, ReturnsVariation, SdilReturn, SmallProducer, Warehouse, extractTotal, listItemsWithTotal}
import sdil.uniform.SdilComponents.ProducerType
import uk.gov.hmrc.http.HeaderCarrier
import views.html.uniform
import views.html.uniform.helpers.dereg_variations_cya

import scala.concurrent.duration.DurationInt
import java.time.LocalDate
import scala.concurrent.{Await, ExecutionContext, Future}

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

  sealed trait Change {
    val data: VariationData
  }
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

  def ChangeCheckProduction(variation: VariationsJourney.Change.RegChange): Boolean = {

    val newList = variation.data.updatedProductionSites
      .filter { x =>
        x.closureDate.fold(true) {
          _.isAfter(LocalDate.now)
        }
      }
      .map(x => x.address)

    if (newList.isEmpty) {
      return false
    } else true
  }

  def ChangeCheckWarehouse(variation: VariationsJourney.Change.RegChange): Boolean = {

    val newList = variation.data.updatedWarehouseSites
      .filter { x =>
        x.closureDate.fold(true) {
          _.isAfter(LocalDate.now)
        }
      }
      .map(x => x.address)

    if (newList.isEmpty) {
      return false
    } else true
  }

  def closedWarehouseSites(variation: VariationsJourney.Change.RegChange, ShowNone: Boolean): List[Site] =
    if (ShowNone == true) {
      val old = variation.data.original.warehouseSites
        .filter { x =>
          x.closureDate.fold(true) {
            _.isAfter(LocalDate.now)
          }
        }
        .map(x => x.address)

      val newList = variation.data.updatedWarehouseSites
        .filter { x =>
          x.closureDate.fold(true) {
            _.isAfter(LocalDate.now)
          }
        }
        .map(x => x.address)

      val diff = old diff newList

      variation.data.original.warehouseSites.filter { site =>
        diff.exists { address =>
          compareAddress(site.address, address)
        }
      }
    } else {
      Nil
    }

  def closedPackagingSites(variation: VariationsJourney.Change.RegChange, ShowNone: Boolean): List[Site] =
    if (ShowNone == true) {
      val old = variation.data.original.productionSites
        .filter { x =>
          x.closureDate.fold(true) {
            _.isAfter(LocalDate.now)
          }
        }
        .map(x => x.address)

      val newList = variation.data.updatedProductionSites
        .filter { x =>
          x.closureDate.fold(true) {
            _.isAfter(LocalDate.now)
          }
        }
        .map(x => x.address)

      val diff = old diff newList

      variation.data.original.productionSites.filter { site =>
        diff.exists { address =>
          compareAddress(site.address, address)
        }
      }
    } else {
      Nil
    }

  def closedPackagingSites3(
    variation: RegistrationVariationData,
    newPackSites: List[Site],
    ShowNone: Boolean): List[Site] =
    if (ShowNone == false) {
      val old = variation.original.productionSites
        .filter { x =>
          x.closureDate.fold(true) {
            _.isAfter(LocalDate.now)
          }
        }
        .map(x => x.address)

      val newList = newPackSites
        .filter { x =>
          x.closureDate.fold(true) {
            _.isAfter(LocalDate.now)
          }
        }
        .map(x => x.address)

      val diff = old diff newList

      variation.original.productionSites.filter { site =>
        diff.exists { address =>
          compareAddress(site.address, address)
        }
      }
    } else {
      Nil
    }

  def closedWarehouseSites3(
    variation: RegistrationVariationData,
    newWarehouseSites: List[Site],
    ShowNone: Boolean): List[Site] =
    if (ShowNone == false) {
      val old = variation.original.warehouseSites
        .filter { x =>
          x.closureDate.fold(true) {
            _.isAfter(LocalDate.now)
          }
        }
        .map(x => x.address)

      val newList = newWarehouseSites
        .filter { x =>
          x.closureDate.fold(true) {
            _.isAfter(LocalDate.now)
          }
        }
        .map(x => x.address)

      val diff = old diff newList

      variation.original.warehouseSites.filter { site =>
        diff.exists { address =>
          compareAddress(site.address, address)
        }
      }
    } else {
      Nil
    }

  def newPackagingSites3(variation: RegistrationVariationData, newPackSites: List[Site]): List[Site] = {
    val old = variation.original.productionSites.map(x => x.address)
    val updated = newPackSites.map(x => x.address)
    val newAddress = updated diff old

    newPackSites.filter { site =>
      newAddress.exists { address =>
        compareAddress(site.address, address)
      }
    }
  }.toList

  def newWarehouseSites3(variation: RegistrationVariationData, newWarehouseSites: List[Site]): List[Site] = {
    val oldAddress = variation.original.warehouseSites.map(x => x.address)
    val updatedAddress = newWarehouseSites.map(x => x.address) //This contains all the packaging sites new and old
    val newAddress = updatedAddress diff oldAddress

    newWarehouseSites.filter { site =>
      newAddress.exists { address =>
        compareAddress(site.address, address)
      }
    }
  }.toList

  def newPackagingSites(variation: VariationsJourney.Change.RegChange): List[Site] = {
    val old = variation.data.original.productionSites.map(x => x.address)
    val updated = variation.data.updatedProductionSites.map(x => x.address)
    val newAddress = updated diff old

    variation.data.updatedProductionSites.filter { site =>
      newAddress.exists { address =>
        compareAddress(site.address, address)
      }
    }
  }.toList

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

  def contactAndBusinessUpdateCheck(variation: VariationsJourney.Change.RegChange): List[String] = {
    val updatedContactDetails = List(
      variation.data.updatedContactDetails.email,
      variation.data.updatedContactDetails.phoneNumber,
      variation.data.updatedContactDetails.fullName,
      variation.data.updatedContactDetails.position
    )
    val originalContactDetails = List(
      variation.data.original.contact.email,
      variation.data.original.contact.phoneNumber,
      variation.data.original.contact.name.getOrElse(""),
      variation.data.original.contact.positionInCompany.getOrElse("")
    )

    val updatedBusinessDetails = List(
      variation.data.updatedBusinessAddress.postcode,
      variation.data.updatedBusinessAddress.line1,
      variation.data.updatedBusinessAddress.line2,
      variation.data.updatedBusinessAddress.line3,
      variation.data.updatedBusinessAddress.line4
    ).filter(_.length > 1)

    val originalBusinessDetails = List(
      variation.data.original.address.postCode
    ) ++ variation.data.original.address.lines

    if ((updatedContactDetails equals (originalContactDetails)) && (updatedBusinessDetails equals originalBusinessDetails) || (updatedContactDetails != (originalContactDetails)) && (updatedBusinessDetails != originalBusinessDetails)) {
      List("contact-details", "business-address")
    } else if (updatedContactDetails equals (originalContactDetails)) { List("business-address") } else
      List("contact-details")
  }

  def changeBusinessAddressJourney(
    id: String,
    subscription: RetrievedSubscription,
    ufViews: views.uniform.Uniform
  )(implicit request: Request[_]) = {
    val data = RegistrationVariationData(subscription)
    import ContactChangeType._
    val changeOptions =
      if (data.isVoluntary)
        ContactChangeType.values.filter(_ != ContactChangeType.Sites)
      else
        ContactChangeType.values

    for {
      _ <- tell(
            key = "change-registered-account-details",
            value = ufViews.updateBusinessAddresses(id, subscription, Address.fromUkAddress(subscription.address))(
              implicitly,
              _: Messages),
            customContent = message("change-registered-account-details.caption", subscription.orgName)
          )

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

      contactDetails = ContactDetails(contact.fullName, contact.position, contact.phoneNumber, contact.email)
      businessDetails = Address(
        businessAddress.line1,
        businessAddress.line2,
        businessAddress.line3,
        businessAddress.line4,
        businessAddress.postcode)

    } yield {
      Change.RegChange(
        data
          .copy(
            updatedBusinessAddress = businessAddress,
            updatedProductionSites = packSites,
            updatedWarehouseSites = warehouses,
            updatedContactDetails = contact
          )
      )
    }
  }

  def deregisterUpdate(
    subscription: RetrievedSubscription,
    pendingReturns: List[ReturnPeriod]
  )(implicit ufMessages: UniformMessages[Html]) = {

    val data = RegistrationVariationData(subscription)
    if (pendingReturns.nonEmpty) {
      end(
        "file-return-before-deregistration",
        uniform.fragments.return_before_dereg("file-return-before-deregistration", pendingReturns)
      )
    } else {
      for {
        reason <- ask[String](
                   "cancel-registration-reason",
                   validation = Rule.nonEmpty[String] followedBy Rule.maxLength[String](255)
                 )
        deregDate <- ask[LocalDate](
                      "cancel-registration-date",
                      validation = Rule.between[LocalDate](LocalDate.now, LocalDate.now.plusDays(15))
                    )

        _ <- tell(
              "check-answers",
              views.html.uniform.fragments.variations_cya(
                data,
                newPackagingSites3(data, data.updatedProductionSites.toList),
                closedPackagingSites3(data, data.updatedProductionSites.toList, false),
                newWarehouseSites3(data, data.updatedWarehouseSites.toList),
                closedWarehouseSites3(data, data.updatedWarehouseSites.toList, false),
                if (data.isVoluntary || pendingReturns.isEmpty) {
                  List("cancel-registration-date", "cancel-registration-reason")
                } else List(""),
              )(_: Messages)
            )
      } yield
        Change.RegChange(
          data.copy(
            reason = reason.some,
            deregDate = deregDate.some
          )
        )
    }
  }

  def contactUpdate(
    subscription: RetrievedSubscription,
  ) = {

    val data = RegistrationVariationData(subscription)

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

      contactDetails = ContactDetails(contact.fullName, contact.position, contact.phoneNumber, contact.email)
      businessDetails = Address(
        businessAddress.line1,
        businessAddress.line2,
        businessAddress.line3,
        businessAddress.line4,
        businessAddress.postcode)

      _ <- tell(
            "check-answers",
            views.html.uniform.fragments.variations_cya(
              RegistrationVariationData(
                data.original,
                businessDetails,
                data.producer,
                data.usesCopacker,
                data.packageOwn,
                data.packageOwnVol,
                data.copackForOthers,
                data.copackForOthersVol,
                data.imports,
                data.importsVol,
                data.updatedProductionSites,
                data.updatedWarehouseSites,
                contactDetails,
                data.previousPages,
                data.reason,
                data.deregDate
              ),
              newPackagingSites3(data, packSites),
              closedPackagingSites3(data, packSites, false),
              newWarehouseSites3(data, warehouses),
              closedWarehouseSites3(data, warehouses, false),
              if (change.contains(ContactPerson) && change.contains(ContactAddress)) {
                List("business-address", "contact-details")
              } else if (change.contains(ContactPerson)) {
                List("contact-details")
              } else if (change.contains(ContactAddress)) {
                List("business-address")
              } else { List("") },
            )(_: Messages)
          )

    } yield {
      Change.RegChange(
        data
          .copy(
            updatedBusinessAddress = businessAddress,
            updatedProductionSites = packSites,
            updatedWarehouseSites = warehouses,
            updatedContactDetails = contact
          )
      )
    }
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
                        deregVariation <- deregisterUpdate(subscription, returnPeriods)
                        _              <- tell("check-answers", dereg_variations_cya(showChangeLinks = true, data)(_: Messages))
                      } yield deregVariation: Change
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
                      _ <- tell(
                            "check-answers",
                            views.html.uniform.fragments.variations_cya(
                              data,
                              newPackagingSites3(data, data.updatedProductionSites.toList),
                              closedPackagingSites3(data, data.updatedProductionSites.toList, false),
                              newWarehouseSites3(data, data.updatedWarehouseSites.toList),
                              closedWarehouseSites3(data, data.updatedWarehouseSites.toList, false),
                              if (!shouldDereg) {
                                List("business-address")
                              } else { List("") },
                            )(_: Messages)
                          )
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
    } yield variation: Change

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
      _         <- tell("check-answers", CYA(variation))
    } yield variation.data
  }

  def activityUpdateJourney(
    subscription: RetrievedSubscription,
    returnPeriods: List[ReturnPeriod]
  )(implicit ec: ExecutionContext, ufMessages: UniformMessages[Html]) = {
    val data = RegistrationVariationData(subscription)
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
                        deregVariation <- deregisterUpdate(subscription, returnPeriods)
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

                      _ <- tell(
                            "check-answers",
                            views.html.uniform.fragments.variations_cya(
                              data.orig,
                              newPackagingSites3(data, data.updatedProductionSites.toList),
                              closedPackagingSites3(data, data.updatedProductionSites.toList, false),
                              newWarehouseSites3(data, data.updatedWarehouseSites.toList),
                              closedWarehouseSites3(data, data.updatedWarehouseSites.toList, false),
                              List("amount-produced"),
                            )(_: Messages)
                          )

                    } yield
                      Change.RegChange(
                        data.copy(
                          producer =
                            Producer(producerType != ProducerType.XNot, (producerType == ProducerType.Large).some),
                          usesCopacker = useCopacker.some,
                          packageOwn =
                            if (packageOwn
                                  .getOrElse(0) == 0) {
                              Some(false)
                            } else if ((packageOwn.get._1 + packageOwn.get._2) > 0) {
                              Some(true)
                            } else {
                              Some(false)
                            },
                          packageOwnVol = longTupToLitreage(packageOwn),
                          copackForOthers = if ((copacks._1 + copacks._2) == 0) {
                            false
                          } else {
                            true
                          },
                          copackForOthersVol = longTupToLitreage(copacks),
                          imports = if ((imports._1 + imports._2) == 0) {
                            false
                          } else {
                            true
                          },
                          importsVol = longTupToLitreage(imports),
                          updatedProductionSites = packSites,
                          updatedWarehouseSites = warehouses
                        ))
                  }
    } yield variation
  }

  def selectJourney(
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
    } yield (changeType)
  }

  val costLower = BigDecimal("0.18")
  val costHigher = BigDecimal("0.24")

  def taxEstimation(r: SdilReturn): BigDecimal = {
    val t = r.packLarge |+| r.importLarge |+| r.ownBrand
    (t._1 * costLower |+| t._2 * costHigher) * 4
  }

  def withReturnsJourney(
    id: String,
    subscription: RetrievedSubscription,
    default: Option[SdilReturn] = None,
    sdilRef: String,
    variableReturns: List[ReturnPeriod],
    pendingReturns: List[ReturnPeriod],
    connector: SoftDrinksIndustryLevyConnector,
    checkSmallProducerStatus: (String, ReturnPeriod) => Future[Option[Boolean]],
    getReturn: ReturnPeriod => Future[Option[SdilReturn]],
    submitReturnVariation: ReturnsVariation => Future[Unit],
    config: AppConfig,
  )(implicit ec: ExecutionContext, hc: HeaderCarrier, ufMessages: UniformMessages[Html]) = {
    val base = RegistrationVariationData(subscription)
    val isVoluntary = subscription.activity.voluntaryRegistration
    for {
      period <- ask[ReturnPeriod](
                 "select-return",
                 validation = Rule.in(variableReturns)
               )
      isSmallProd <- convertWithKey("is-small-producer")(checkSmallProducerStatus(sdilRef, period))
      origReturn  <- convertWithKey("return-lookup")(getReturn(period))
      broughtForward <- if (config.balanceAllEnabled) {
                         convertWithKey("balance-history") {
                           connector.balanceHistory(sdilRef, withAssessment = false).map { x =>
                             extractTotal(listItemsWithTotal(x))
                           }
                         }
                       } else {
                         convertWithKey("balance")(connector.balance(sdilRef, withAssessment = false))
                       }
      newReturn <- (for {
                    ownBrands <- askEmptyOption[(Long, Long)](
                                  "own-brands-packaged-at-own-sites",
                                  default = default.map {
                                    _.ownBrand
                                  }
                                ) emptyUnless !subscription.activity.smallProducer
                    contractPacked <- askEmptyOption[(Long, Long)](
                                       "packaged-as-a-contract-packer",
                                       default = default.map {
                                         _.packLarge
                                       }
                                     )
                    smallProds <- askList[SmallProducer]("small-producer-details", default.map {
                                   _.packSmall
                                 }, Rule.nonEmpty[List[SmallProducer]])(
                                   {
                                     case (index: Option[Int], existingSmallProducers: List[SmallProducer]) =>
                                       ask[SmallProducer](
                                         s"add-small-producer",
                                         default = index.map(existingSmallProducers),
                                         validation = Rule.condAtPath[SmallProducer]("sdilRef")(
                                           sp =>
                                             Await
                                               .result(checkSmallProducerStatus(sp.sdilRef, period), 20.seconds)
                                               .getOrElse(true),
                                           "notSmall"
                                         ) followedBy Rule.condAtPath[SmallProducer]("sdilRef")(
                                           sp => !(sp.sdilRef === subscription.sdilRef),
                                           "same"
                                         ) followedBy Rule.condAtPath[SmallProducer]("sdilRef")(
                                           sp =>
                                             !(id
                                               .contains("/add/") && existingSmallProducers
                                               .map(s => s.sdilRef)
                                               .contains(sp.sdilRef)),
                                           "alreadyexists"
                                         )
                                       )
                                   }, {
                                     case (index: Int, existingSmallProducers: List[SmallProducer]) =>
                                       interact[Boolean](
                                         "remove-small-producer-details",
                                         existingSmallProducers(index).sdilRef)
                                   }
                                 ) emptyUnless ask[Boolean]("exemptions-for-small-producers", default = default.map {
                                   _.packSmall.nonEmpty
                                 })
                    imports <- askEmptyOption[(Long, Long)]("brought-into-uk", default.map {
                                _.importLarge
                              })
                    importsSmall <- askEmptyOption[(Long, Long)]("brought-into-uk-from-small-producers", default.map {
                                     _.importSmall
                                   })
                    exportCredits <- askEmptyOption[(Long, Long)]("claim-credits-for-exports", default.map {
                                      _.export
                                    })
                    wastage <- askEmptyOption[(Long, Long)]("claim-credits-for-lost-damaged", default.map {
                                _.wastage
                              })
                    sdilReturn = SdilReturn(
                      ownBrands,
                      contractPacked,
                      smallProds,
                      imports,
                      importsSmall,
                      exportCredits,
                      wastage)
                    isNewImporter = (sdilReturn.totalImported._1 > 0L && sdilReturn.totalImported._2 > 0L) && !subscription.activity.importer
                    isNewPacker = (sdilReturn.totalPacked._1 > 0L && sdilReturn.totalPacked._2 > 0L) && !subscription.activity.contractPacker
                    inner = uniform.fragments.return_variation_continue(isNewImporter, isNewPacker)(_: Messages)
                    _ <- tell("return-change-registration", inner) when isNewImporter || isNewPacker
                    newPackingSites <- (
                                        for {
                                          firstPackingSite <- interact[Boolean](
                                                               "pack-at-business-address-in-return",
                                                               Address.fromUkAddress(subscription.address)) flatMap {
                                                               case true =>
                                                                 pure(Address.fromUkAddress(subscription.address))
                                                               case false => ask[Address]("first-production-site")
                                                             }
                                          packingSites <- askListSimple[Address](
                                                           "production-site-details",
                                                           "site-in-return",
                                                           default = Some(firstPackingSite :: Nil),
                                                           listValidation = Rule.nonEmpty[List[Address]]
                                                         ).map(_.map(Site.fromAddress))
                                        } yield packingSites
                                      ) when isNewPacker && subscription.productionSites.isEmpty
                    newWarehouses <- (for {
                                      addWarehouses <- ask[Boolean]("ask-secondary-warehouses-in-return")
                                      warehouses <- askListSimple[Warehouse](
                                                     "secondary-warehouse-details",
                                                     "warehouse-in-return",
                                                     listValidation = Rule.nonEmpty[List[Warehouse]]
                                                   ) map (_.map(Site.fromWarehouse)) emptyUnless addWarehouses
                                    } yield warehouses) when isNewImporter && subscription.warehouseSites.isEmpty
                    data = returnAmount(sdilReturn, isSmallProd.getOrElse(true))
                    subtotal = calculateSubtotal(data)
                    total = subtotal - broughtForward
                    thisVariation = ReturnsVariation(
                      orgName = subscription.orgName,
                      ppobAddress = subscription.address,
                      importer = (isNewImporter, (sdilReturn.totalImported).combineN(4)),
                      packer = (isNewPacker, (sdilReturn.totalPacked).combineN(4)),
                      warehouses = newWarehouses.getOrElse(List.empty[Site]),
                      packingSites = newPackingSites.getOrElse(List.empty[Site]),
                      phoneNumber = subscription.contact.phoneNumber,
                      email = subscription.contact.email,
                      taxEstimation = taxEstimation(sdilReturn)
                    )
                    _ <- convertWithKey("vary-in-return")(submitReturnVariation(thisVariation))
                    _ <- tell(
                          "check-your-answers",
                          uniform.fragments.returnsCYA(
                            key = "check-your-answers",
                            lineItems = data,
                            costLower = costLower,
                            costHigher = costHigher,
                            subtotal = subtotal,
                            broughtForward = broughtForward,
                            total = total,
                            variation = thisVariation.some,
                            subscription = subscription,
                            period = period,
                            originalReturn = None
                          )(_: Messages)
                        )
                  } yield (sdilReturn, thisVariation))

      emptyReturn = SdilReturn((0, 0), (0, 0), Nil, (0, 0), (0, 0), (0, 0), (0, 0), None)
      variation = ReturnVariationData(
        origReturn.getOrElse(emptyReturn),
        newReturn._1,
        period,
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
    } yield { payMethodAndReason }
  }
}
