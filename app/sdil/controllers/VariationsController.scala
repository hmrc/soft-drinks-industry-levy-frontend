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

package sdil.controllers

import cats.data.OptionT

import scala.language.{higherKinds, postfixOps}
import cats.implicits._

import java.time.{LocalDate, LocalTime, ZoneId}
import ltbs.uniform._
import play.api.i18n._
import play.api.Logger
import play.api.i18n.I18nSupport
import play.api.mvc._
import play.twirl.api.Html

import scala.concurrent._
import sdil.actions._
import sdil.config._
import sdil.connectors.SoftDrinksIndustryLevyConnector
import sdil.models._
import sdil.models.backend.{Site, Subscription, UkAddress}
import sdil.models.retrieved.RetrievedSubscription
import sdil.models.variations._
import sdil.uniform.SdilComponents.ProducerType
import sdil.uniform.SaveForLaterPersistenceNew
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import ltbs.uniform.interpreters.playframework.SessionPersistence
import sdil.forms.selectJourneyForm
import sdil.journeys.VariationsJourney
import sdil.journeys.VariationsJourney.{ChangeCheckProduction, _}
import views.Views
import views.html.{main_template, uniform}

import scala.concurrent.duration.DurationInt
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatter.ofPattern
import javax.inject.Inject

class VariationsController @Inject()(
  mcc: MessagesControllerComponents,
  val config: AppConfig,
  val ufViews: views.uniform.Uniform,
  errorHandler: SDILErrorHandler,
  registeredAction: RegisteredAction,
  sdilConnector: SoftDrinksIndustryLevyConnector,
  regCache: RegistrationFormDataCache,
  regVariationsCache: RegistrationVariationFormDataCache,
  returnsVariationsCache: ReturnVariationFormDataCache,
)(implicit ec: ExecutionContext)
    extends FrontendController(mcc) with I18nSupport with HmrcPlayInterpreter {

  override def defaultBackLink = "/soft-drinks-industry-levy"

  override def blockCollections[X[_] <: Traversable[_], A]: common.web.WebAsk[Html, X[A]] = ???

  override def blockCollections2[X[_] <: Traversable[_], A]: common.web.WebAsk[Html, X[A]] = ???

  implicit lazy val codecOptRet: common.web.Codec[Option[SdilReturn]] = {
    import java.time.LocalDateTime
    implicit def c: common.web.Codec[LocalDateTime] =
      stringField.simap { s =>
        Either
          .catchOnly[java.time.format.DateTimeParseException](
            LocalDateTime.parse(s)
          )
          .leftMap(_ => ErrorTree.oneErr(ErrorMsg("not-a-date")))
      }(_.toString)

    common.web.InferCodec.gen[Option[SdilReturn]]
  }

  implicit lazy val persistence =
    SaveForLaterPersistenceNew[RegisteredRequest[AnyContent]](_.sdilEnrolment.value)("variations", regCache)

  def selectJourney(id: String): Action[AnyContent] = registeredAction.async { implicit request =>
    val sdilRef = request.sdilEnrolment.value
    var hasNotSelected: Boolean = false

    sdilConnector.retrieveSubscription(sdilRef) flatMap {
      case Some(subscription) =>
        val base = RegistrationVariationData(subscription)
        val returns: Boolean =
          if ((Await.result(sdilConnector.returns_variable(base.original.utr), 10 seconds).isEmpty)) {
            false
          } else true
        Ok(
          main_template(
            Messages("return-sent.title")
          )(
            views.html.uniform.fragments.selectJourney(
              "variationDone",
              selectJourneyForm.form,
              id,
              returns,
              hasNotSelected
            )
          )(implicitly, implicitly, config))

      case None => Future.successful(NotFound(""))
    }
  }

  def selectJourneyPost(id: String) = registeredAction.async { implicit request =>
    val sdilRef = request.sdilEnrolment.value
    var hasNotSelected: Boolean = false
    sdilConnector.retrieveSubscription(sdilRef) flatMap {
      case Some(subscription) =>
        val base = RegistrationVariationData(subscription)
        val returns: Boolean =
          if ((Await.result(sdilConnector.returns_variable(base.original.utr), 10 seconds).isEmpty)) {
            false
          } else true
        selectJourneyForm.form.bindFromRequest.fold(
          formWithErrors => {
            Ok(
              main_template(
                Messages("return-sent.title")
              )(
                views.html.uniform.fragments.selectJourney(
                  "variationDone",
                  selectJourneyForm.form,
                  id,
                  returns,
                  hasNotSelected = true
                )
              )(implicitly, implicitly, config))
          },
          formData => {
            val form = formData.toString
            form match {
              case "selectJourneyForm(AASites)"    => Redirect(routes.VariationsController.contactUpdateJourney(id = ""))
              case "selectJourneyForm(Activity)"   => Redirect(routes.VariationsController.activityUpdateJourney(id = ""))
              case "selectJourneyForm(Deregister)" => Redirect(routes.VariationsController.deregisterUpdate(id = ""))
              case "selectJourneyForm(Returns)"    => Redirect(routes.VariationsReturnsController.journey(id = ""))
            }
          }
        )
    }
  }

  def changeAddressAndContact(id: String) = registeredAction.async { implicit request =>
    val sdilRef = request.sdilEnrolment.value
    sdilConnector.retrieveSubscription(sdilRef) flatMap {
      case Some(subscription) =>
        interpret(
          VariationsJourney.changeBusinessAddressJourney(id, subscription, ufViews)(request)
        ).run(id, purgeStateUponCompletion = true) {
          case Change.RegChange(reg) =>
            sdilConnector.submitVariation(Convert(reg), sdilRef).flatMap { _ =>
              regVariationsCache.cache(sdilRef, reg).flatMap { _ =>
                logger.info("variation of Address and Contact  is complete")
                Redirect(routes.VariationsController.showVariationsComplete())
              }
            }
          case _ =>
            logger.warn("registrationVariation for address and contact subJourney has failed")
            Redirect(routes.ServicePageController.show)
        }
      case None => Future.successful(NotFound(""))
    }
  }

  def activityUpdateJourney(id: String) = registeredAction.async { implicit request =>
    val sdilRef = request.sdilEnrolment.value
    sdilConnector.retrieveSubscription(sdilRef) flatMap {
      case Some(subscription) =>
        val base = RegistrationVariationData(subscription)
        val returnPeriods = sdilConnector.returns_pending(subscription.utr)
        interpret(
          VariationsJourney.activityUpdateJourney(subscription, Await.result((returnPeriods), 10.seconds))
        ).run(id) {
          case Change.RegChange(reg) =>
            sdilConnector.submitVariation(Convert(reg), sdilRef).flatMap { _ =>
              regVariationsCache.cache(sdilRef, reg).flatMap { _ =>
                logger.info("variation of Address and Contact  is complete")
                Redirect(routes.VariationsController.showVariationsComplete())
              }
            }
          case _ =>
            logger.warn("registrationVariation for address and contact subJourney has failed")
            Redirect(routes.ServicePageController.show)
        }
      case None => Future.successful(NotFound(""))
    }
  }

  def contactUpdateJourney(id: String) = registeredAction.async { implicit request =>
    val sdilRef = request.sdilEnrolment.value
    sdilConnector.retrieveSubscription(sdilRef) flatMap {
      case Some(subscription) =>
        interpret(
          VariationsJourney.contactUpdate(subscription)
        ).run(id, purgeStateUponCompletion = true) {
          case Change.RegChange(reg) =>
            sdilConnector.submitVariation(Convert(reg), sdilRef).flatMap { _ =>
              regVariationsCache.cache(sdilRef, reg).flatMap { _ =>
                logger.info("variation of Address and Contact  is complete")
                Redirect(routes.VariationsController.showVariationsComplete())
              }
            }
          case _ =>
            logger.warn("registrationVariation for address and contact subJourney has failed")
            Redirect(routes.ServicePageController.show)
        }
      case None => Future.successful(NotFound(""))
    }
  }

  def deregisterUpdate(id: String) = registeredAction.async { implicit request =>
    val sdilRef = request.sdilEnrolment.value
    sdilConnector.retrieveSubscription(sdilRef) flatMap {
      case Some(subscription) =>
        val base = RegistrationVariationData(subscription)
        val variableReturn = sdilConnector.returns_variable(base.original.utr)
        interpret(
          VariationsJourney.deregisterUpdate(subscription, Await.result((variableReturn), 10.seconds))
        ).run(id) {
          case Change.RegChange(reg) =>
            sdilConnector.submitVariation(Convert(reg), sdilRef).flatMap { _ =>
              regVariationsCache.cache(sdilRef, reg).flatMap { _ =>
                logger.info("variation of Address and Contact  is complete")
                Redirect(routes.VariationsController.showVariationsComplete())
              }
            }
          case _ =>
            logger.warn("registrationVariation for address and contact subJourney has failed")
            Redirect(routes.ServicePageController.show)
        }
      case None => Future.successful(NotFound(""))
    }
  }

  val logger: Logger = Logger(this.getClass())

  def ChangeCheckProduction(variation: RegistrationVariationData): Boolean = {

    val newList = variation.updatedProductionSites
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

  def ChangeCheckWarehouse(variation: RegistrationVariationData): Boolean = {

    val newList = variation.updatedWarehouseSites
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

  def closedWarehouseSites(variation: RegistrationVariationData, ShowNone: Boolean): List[Site] =
    if (ShowNone == true) {
      val old = variation.original.warehouseSites
        .filter { x =>
          x.closureDate.fold(true) {
            _.isAfter(LocalDate.now)
          }
        }
        .map(x => x.address)

      val newList = variation.updatedWarehouseSites
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

  def closedPackagingSites(variation: RegistrationVariationData, ShowNone: Boolean): List[Site] =
    if (ShowNone == true) {
      val old = variation.original.productionSites
        .filter { x =>
          x.closureDate.fold(true) {
            _.isAfter(LocalDate.now)
          }
        }
        .map(x => x.address)

      val newList = variation.updatedProductionSites
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

  def newPackagingSites(variation: RegistrationVariationData): List[Site] = {
    val old = variation.original.productionSites.map(x => x.address)
    val updated = variation.updatedProductionSites.map(x => x.address)
    val newAddress = updated diff old

    variation.updatedProductionSites.filter { site =>
      newAddress.exists { address =>
        compareAddress(site.address, address)
      }
    }
  }.toList

  def newWarehouseSites(variation: RegistrationVariationData): List[Site] = {
    val oldAddress = variation.original.warehouseSites.map(x => x.address)
    val updatedAddress = variation.updatedWarehouseSites.map(x => x.address) //This contains all the packaging sites new and old
    val newAddress = updatedAddress diff oldAddress

    variation.updatedWarehouseSites.filter { site =>
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
        x.closureDate.get.isAfter(LocalDate.now)
      }
      .filter(x => closedSites.contains(x.ref.getOrElse("")))

  def contactAndBusinessUpdateCheck(variation: RegistrationVariationData): List[String] = {
    val updatedContactDetails = List(
      variation.updatedContactDetails.email,
      variation.updatedContactDetails.phoneNumber,
      variation.updatedContactDetails.fullName,
      variation.updatedContactDetails.position
    )
    val originalContactDetails = List(
      variation.original.contact.email,
      variation.original.contact.phoneNumber,
      variation.original.contact.name.getOrElse(""),
      variation.original.contact.positionInCompany.getOrElse("")
    )

    val updatedBusinessDetails = List(
      variation.updatedBusinessAddress.postcode,
      variation.updatedBusinessAddress.line1,
      variation.updatedBusinessAddress.line2,
      variation.updatedBusinessAddress.line3,
      variation.updatedBusinessAddress.line4
    ).filter(_.length > 1)

    val originalBusinessDetails = List(
      variation.original.address.postCode
    ) ++ variation.original.address.lines

    if ((updatedContactDetails equals (originalContactDetails)) && (updatedBusinessDetails equals originalBusinessDetails) || (updatedContactDetails != (originalContactDetails)) && (updatedBusinessDetails != originalBusinessDetails)) {
      List("contact-details", "business-address")
    } else if (updatedContactDetails equals (originalContactDetails)) { List("business-address") } else
      List("contact-details")
  }

  def showVariationsComplete() = registeredAction.async { implicit request =>
    val sdilRef = request.sdilEnrolment.value
    for {
      regDB <- regVariationsCache.get(sdilRef)
      retDB <- returnsVariationsCache.get(sdilRef)
      subscription <- sdilConnector.retrieveSubscription(sdilRef).map {
                       _.get
                     }
      broughtForward <- if (config.balanceAllEnabled)
                         sdilConnector.balanceHistory(sdilRef, withAssessment = false).map { x =>
                           extractTotal(listItemsWithTotal(x))
                         } else sdilConnector.balance(sdilRef, withAssessment = false)
    } yield {
      (regDB, retDB) match {
        case (Some(regVar), _) =>
          val whnKey = regVar match {
            case rv if rv.manToVol => "manToVol".some
            case rv if rv.volToMan => "volToMan".some
            case _                 => None
          }
          val whn = uniform.fragments.variationsWHN(
            contactAndBusinessUpdateCheck(regVar),
            newPackagingSites(regVar),
            closedPackagingSites(regVar, ShowNone = ChangeCheckProduction(regVar)),
            newWarehouseSites(regVar),
            closedWarehouseSites(regVar, ShowNone = ChangeCheckWarehouse(regVar)),
            regVar.some,
            None,
            whnKey
          )
          val varySubheading = Html(
            Messages(
              if (regVar.deregDate.nonEmpty) {
                "variationDone.your.request"
              } else {
                "returnVariationDone.your.updates"
              },
              subscription.orgName,
              LocalDate.now.format(ofPattern("d MMMM yyyy")),
              LocalTime.now(ZoneId.of("Europe/London")).format(DateTimeFormatter.ofPattern("h:mma")).toLowerCase
            )).some

          Ok(
            main_template(
              Messages("return-sent.title")
            )(
              views.html.uniform
                .journeyEndNew(
                  "variationDone",
                  whatHappensNext = whn.some,
                  updateTimeMessage = varySubheading,
                  email = subscription.contact.email.some
                )
            )(implicitly, implicitly, config))

        case (_, Some(retVar)) =>
          val whn = uniform.fragments
            .variationsWHN(a = retVar.some, key = Some("return"), broughtForward = broughtForward.some)

          val retSubheading = uniform.fragments.return_variation_done_subheading(subscription, retVar.period).some
          Ok(
            main_template(
              Messages("return-sent.title")
            )(
              views.html.uniform
                .journeyEndNew(
                  "returnVariationDone",
                  whatHappensNext = whn.some,
                  updateTimeMessage = retSubheading
                )
            )(implicitly, implicitly, config))
      }
    }
  }

  def changeActorStatus(id: String): Action[AnyContent] = registeredAction.async { implicit request =>
    val sdilRef = request.sdilEnrolment.value
    val x = sdilConnector.retrieveSubscription(sdilRef)

    x flatMap {
      case Some(subscription) =>
        val base = RegistrationVariationData(subscription)

        def getReturn(period: ReturnPeriod): Future[Option[SdilReturn]] =
          sdilConnector.returns_get(subscription.utr, period)

        def checkSmallProducerStatus(sdilRef: String, period: ReturnPeriod): Future[Option[Boolean]] =
          sdilConnector.checkSmallProducerStatus(sdilRef, period)

        def submitReturnVariation(rvd: ReturnsVariation): Future[Unit] =
          sdilConnector.returns_variation(rvd, sdilRef)

        def submitAdjustment(rvd: ReturnVariationData) =
          sdilConnector.returns_vary(sdilRef, rvd)

        for {
          variableReturns <- sdilConnector.returns_variable(base.original.utr)
          returnPeriods   <- sdilConnector.returns_pending(subscription.utr)
          response <- interpret(
                       VariationsJourney.changeActorJourney(
                         id,
                         subscription,
                         sdilRef,
                         variableReturns,
                         returnPeriods,
                         sdilConnector,
                         checkSmallProducerStatus,
                         getReturn,
                         submitReturnVariation,
                         config
                       ))
                       .run(id, purgeStateUponCompletion = true, config = journeyConfig) {
                         case reg: RegistrationVariationData =>
                           sdilConnector.submitVariation(Convert(reg), sdilRef).flatMap { _ =>
                             regVariationsCache.cache(sdilRef, reg).flatMap { _ =>
                               logger.info("variation of Registration is complete")
                               Redirect(routes.VariationsController.showVariationsComplete())
                             }
                           }
                       }
        } yield response
      case None => Future.successful(NotFound(""))
    }
  }
}
