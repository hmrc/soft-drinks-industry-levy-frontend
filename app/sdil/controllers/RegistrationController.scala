/*
 * Copyright 2023 HM Revenue & Customs
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

import java.time.LocalDate
import ltbs.uniform._
import validation._
import play.api.i18n._
import play.api.mvc._
import play.twirl.api.Html

import scala.concurrent.{ExecutionContext, Future}
import cats.instances.duration._

import scala.language.higherKinds
import sdil.actions.{AuthorisedAction, AuthorisedRequest}
import sdil.config.{AppConfig, RegistrationFormDataCache}
import sdil.connectors.SoftDrinksIndustryLevyConnector
import sdil.models._
import sdil.models.backend._
import sdil.uniform._
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.uniform
import play.api.Logger
import sdil.uniform.SdilComponents.{OrganisationType, OrganisationTypeSoleless, ProducerType}

import javax.inject.Inject

class RegistrationController @Inject()(
  authorisedAction: AuthorisedAction,
  sdilConnector: SoftDrinksIndustryLevyConnector,
  cache: RegistrationFormDataCache,
  mcc: MessagesControllerComponents,
  val config: AppConfig,
  val ufViews: views.uniform.Uniform
)(implicit ec: ExecutionContext)
    extends FrontendController(mcc) with I18nSupport with HmrcPlayInterpreter {

  override def defaultBackLink = "/soft-drinks-industry-levy"

  implicit lazy val persistence =
    SaveForLaterPersistenceNew[AuthorisedRequest[AnyContent]](_.internalId)("registration", cache)

  lazy val logger = Logger(this.getClass())

  def index(id: String): Action[AnyContent] = authorisedAction.async { implicit request =>
    val hasCTEnrolment = request.enrolments.getEnrolment("IR-CT").isDefined

    cache.get(request.internalId) flatMap {
      case Some(fd) =>
        def backendCall(s: Subscription): Future[Unit] =
          sdilConnector.submit(Subscription.desify(s), fd.rosmData.safeId)

        interpret(RegistrationController.journey(hasCTEnrolment, fd, backendCall))
          .run(id, purgeStateUponCompletion = true, config = journeyConfig) { _ =>
            Redirect(routes.ServicePageController.show)
          }
      case None =>
        logger.warn("nothing in cache") // TODO we should set the cache and redirect
        NotFound("").pure[Future]
    }
  }
}

object RegistrationController {
  def journey(
    hasCTEnrolment: Boolean,
    fd: RegistrationFormData,
    backendCall: Subscription => Future[Unit]
  )(implicit ec: ExecutionContext, ufMessages: UniformMessages[Html]) =
    for {
      orgType <- if (hasCTEnrolment) ask[OrganisationTypeSoleless]("organisation-type")
                else ask[OrganisationType]("organisation-type")
      _            <- end("partnerships", uniform.fragments.partnerships()(_: Messages)) when (orgType.entryName == OrganisationType.partnership.entryName)
      producerType <- ask[ProducerType]("producer")
      useCopacker  <- ask[Boolean]("copacked") when producerType == ProducerType.Small
      packageOwn <- askEmptyOption[(Long, Long)](
                     "package-own-uk",
                     validation =
                       Rule.condAtPath("Some", "value", "_1")(x => x.fold(true)(y => (y._1 + y._2) > 0L), "min")
                   ) emptyUnless producerType != ProducerType.XNot
      copacks <- askEmptyOption[(Long, Long)](
                  "package-copack",
                  validation = Rule.condAtPath("Some", "value", "_1")(x => x.fold(true)(y => (y._1 + y._2) > 0L), "min")
                )
      imports <- askEmptyOption[(Long, Long)](
                  "import",
                  validation =
                    Rule.condAtPath("Some", "value", "_1")(x => x.fold(true)(y => (y._1 + y._2) >= 1L), "min")
                )
      noUkActivity: Boolean = if (copacks._1 + copacks._2 == 0L && imports._1 + imports._2 == 0L) { true } else false
      smallProducerWithNoCopacker = producerType != ProducerType.Large && useCopacker.forall(_ == false)
      noReg = uniform.fragments.registration_not_required()(implicitly)
      _ <- end("do-not-register", noReg) when (smallProducerWithNoCopacker && noUkActivity == true)
      isVoluntary = producerType == ProducerType.Small && useCopacker.contains(true) && (copacks._1 + copacks._2 == 0L && imports._1 + imports._2 == 0L)
      regDate <- ask[LocalDate](
                  "start-date",
                  validation =
                    Rule.min(LocalDate.of(2018, 4, 6), "minimum-date") followedBy
                      Rule.max(LocalDate.now, "maximum-date")
                ) unless isVoluntary
      askPackingSites = (producerType == ProducerType.Large && packageOwn > Tuple2(0L, 0L)) || copacks > Tuple2(0L, 0L)
      useBusinessAddress <- interact[Boolean](
                             "pack-at-business-address",
                             fd.rosmData.address
                           ) when askPackingSites

      packingSites = if (useBusinessAddress.getOrElse(false)) {
        List(fd.rosmData.address)
      } else {
        List.empty[Address]
      }

      packSites <- askList[Address]("production-site-details", packingSites.some, Rule.nonEmpty)(
                    {
                      case (index: Option[Int], existingAddresses: List[Address]) =>
                        ask[Address]("p-site", default = index.map(existingAddresses))
                    }, {
                      case (index: Int, existingAddresses: List[Address]) =>
                        interact[Boolean]("remove-packaging-site-details", existingAddresses(index).nonEmptyLines)
                    }
                  ).map(_.map(Site.fromAddress)) emptyUnless askPackingSites

      addWarehouses <- ask[Boolean](
                        "ask-secondary-warehouses"
                      ) when (!isVoluntary)

      warehouseSites = if (addWarehouses.getOrElse(false)) {
        List.empty[Warehouse]
      } else {
        List.empty[Warehouse]
      }

      warehouses <- askList[Warehouse]("warehouses", warehouseSites.some, Rule.nonEmpty)(
                     {
                       case (index: Option[Int], existingWarehouses: List[Warehouse]) =>
                         ask[Warehouse]("w-house", default = index.map(existingWarehouses))
                     }, {
                       case (index: Int, existingWarehouses: List[Warehouse]) =>
                         interact[Boolean]("remove-warehouse-details", existingWarehouses(index).nonEmptyLines)
                     }
                   ).map(_.map(Site.fromWarehouse)) emptyUnless (addWarehouses == Some(true))

      contactDetails <- ask[ContactDetails]("contact-details")
      activity = Activity(
        longTupToLitreage(packageOwn),
        longTupToLitreage(imports),
        longTupToLitreage(copacks),
        useCopacker.collect { case true => Litreage(1, 1) },
        producerType == ProducerType.Large
      )
      declaration = uniform.fragments.registerDeclaration(
        fd,
        producerType match {
          // old packLarge field - consider template refactor
          case ProducerType.Large => Some(true)
          case ProducerType.Small => Some(false)
          case _                  => None
        },
        useCopacker,
        activity.ProducedOwnBrand,
        activity.CopackerAll,
        activity.Imported,
        isVoluntary,
        regDate,
        warehouses,
        packSites,
        contactDetails
      )(_: Messages)
      _ <- tell("declaration", declaration)
      subscription = Subscription(
        fd.utr,
        fd.rosmData.organisationName,
        orgType.toString,
        UkAddress.fromAddress(fd.rosmData.address),
        activity,
        regDate.getOrElse(LocalDate.now),
        packSites,
        warehouses,
        Contact(
          name = Some(contactDetails.fullName),
          positionInCompany = Some(contactDetails.position),
          phoneNumber = contactDetails.phoneNumber,
          email = contactDetails.email
        )
      )
      _ <- convertWithKey("submission")(backendCall(subscription))
      _ <- nonReturn("reg-complete")
      _ <- end(
            "registration-confirmation", { (msg: Messages) =>
              val complete = uniform.fragments.registrationComplete(subscription.contact.email)(msg).some
              val subheading = Html(msg("registration-confirmation.subheading", subscription.orgName)).some
              views.html.uniform.journeyEndNew(
                "registration-confirmation",
                whatHappensNext = complete,
                getTotal = subheading,
                email = contactDetails.email.some)(msg)
            }
          )
    } yield subscription

}
