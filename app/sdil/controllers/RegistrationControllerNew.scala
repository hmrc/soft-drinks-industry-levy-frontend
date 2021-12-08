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
import java.time.LocalDate
import ltbs.play.scaffold.GdsComponents.bool
import ltbs.uniform._, validation._
import ltbs.uniform.common.web.{FutureAdapter, WebMonad}
import ltbs.uniform.interpreters.playframework._
import play.api.i18n._
import play.api.libs.json._
import play.api.mvc._
import play.twirl.api.Html
import scala.concurrent.{ExecutionContext, Future, duration}, duration._
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

class RegistrationControllerNew(
  authorisedAction: AuthorisedAction,
  sdilConnector: SoftDrinksIndustryLevyConnector,
  cache: RegistrationFormDataCache,
  mcc: MessagesControllerComponents,
  val config: AppConfig,
  val ufViews: views.uniform.Uniform,
  implicit val ec: ExecutionContext
) extends FrontendController(mcc) with I18nSupport with HmrcPlayInterpreter {

  override def defaultBackLink = "/soft-drinks-industry-levy"

  implicit lazy val persistence =
    SaveForLaterPersistenceNew[AuthorisedRequest[AnyContent]](_.internalId)("registration", cache.shortLiveCache)

  lazy val logger = Logger(this.getClass())

  def index(id: String): Action[AnyContent] = authorisedAction.async { implicit request =>
    val hasCTEnrolment = request.enrolments.getEnrolment("IR-CT").isDefined

    cache.get(request.internalId) flatMap {
      case Some(fd) =>
        def backendCall(s: Subscription): Future[Unit] =
          sdilConnector.submit(Subscription.desify(s), fd.rosmData.safeId)

        interpret(RegistrationControllerNew.journey(hasCTEnrolment, fd, backendCall)).run(id, config = journeyConfig) {
          _ =>
            Redirect(routes.ServicePageController.show())
        }
      case None =>
        logger.warn("nothing in cache") // TODO we should set the cache and redirect
        NotFound("").pure[Future]
    }
  }
}

object RegistrationControllerNew {

  def journey(
    hasCTEnrolment: Boolean,
    fd: RegistrationFormData,
    backendCall: Subscription => Future[Unit]
  ) =
    for {

      orgType <- if (hasCTEnrolment) ask[OrganisationTypeSoleless]("organisation-type")
                else ask[OrganisationType]("organisation-type")
      _            <- end("partnerships", uniform.fragments.partnerships()(_: Messages)) when (orgType.entryName == OrganisationType.partnership.entryName)
      producerType <- ask[ProducerType]("producer")
      useCopacker  <- ask[Boolean]("copacked") when producerType == ProducerType.Small
      packageOwn <- askEmptyOption[(Long, Long)](
                     "package-own-uk",
                     validation =
                       Rule.condAtPath("Some", "value", "_1")(x => x.fold(true)(y => (y._1 + y._2) >= 1L), "min")
                   ) emptyUnless producerType != ProducerType.Not
      copacks <- askEmptyOption[(Long, Long)](
                  "package-copack",
                  validation =
                    Rule.condAtPath("Some", "value", "_1")(x => x.fold(true)(y => (y._1 + y._2) >= 1L), "min")
                )
      imports <- askEmptyOption[(Long, Long)](
                  "import",
                  validation =
                    Rule.condAtPath("Some", "value", "_1")(x => x.fold(true)(y => (y._1 + y._2) >= 1L), "min")
                )
      noUkActivity = (copacks, imports).isEmpty
      smallProducerWithNoCopacker = producerType != ProducerType.Large && useCopacker.forall(_ == false)
      _ <- end("do-not-register") when (noUkActivity && smallProducerWithNoCopacker)
      isVoluntary = producerType == ProducerType.Small && useCopacker.contains(true) && (copacks, imports).isEmpty
      regDate <- ask[LocalDate]("start-date") unless isVoluntary
      askPackingSites = (producerType == ProducerType.Large && packageOwn.nonEmpty) || copacks.nonEmpty
      useBusinessAddress <- ask[Boolean]("pack-at-business-address") when askPackingSites
      packingSites = if (useBusinessAddress.getOrElse(false)) {
        List(Site.fromAddress(fd.rosmData.address))
      } else {
        List.empty[Site]
      }
      packSites <- askListSimple[Site]("production-sites") emptyUnless askPackingSites
      addWarehouses <- isVoluntary match {
                        case true  => ask[Boolean]("ask-secondary-warehouses")
                        case false => pure(false)
                      }
      warehouses <- askListSimple[Site](
                     "warehouses",
                     validation = Rule.nonEmpty
                   ) emptyUnless addWarehouses
      contactDetails <- ask[ContactDetails]("contact-details")
      activity = Activity(
        longTupToLitreage(packageOwn),
        longTupToLitreage(imports),
        longTupToLitreage(copacks),
        useCopacker.collect { case true => Litreage(1, 1) }, // TODO - why (1,1) ?
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
      _ <- tell(
            "confirmation", { (msg: Messages) =>
              val complete = uniform.fragments.registrationComplete(subscription.contact.email)(msg).some
              val subheading = Html(msg("complete.subheading", subscription.orgName)).some
              views.html.uniform.journeyEndNew("complete", whatHappensNext = complete, getTotal = subheading)(msg)
            }
          )
    } yield subscription

}
