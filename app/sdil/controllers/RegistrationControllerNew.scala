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
import ltbs.play.scaffold.SdilComponents._
import ltbs.uniform._, validation._
import ltbs.uniform.interpreters.playframework._
import play.api.i18n._
import play.api.libs.json._
import play.api.mvc._
import play.twirl.api.Html
import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds
import sdil.actions.{AuthorisedAction, AuthorisedRequest}
import sdil.config.{AppConfig, RegistrationFormDataCache}
import sdil.connectors.SoftDrinksIndustryLevyConnector
import sdil.models._
import sdil.models.backend._
import sdil.uniform.SaveForLaterPersistenceNew
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import uk.gov.hmrc.uniform.playutil.ExtraMessages
import uk.gov.hmrc.uniform.webmonad.clear
import views.html.uniform

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

  object completedPersistence {
    /// TODO Crude implementation - replace with proper persistence
    private var state: Map[String, JsValue] = Map.empty

    def get[A: Reads]()(implicit req: AuthorisedRequest[AnyContent]): Future[Option[A]] =
      Future.successful(state.get(req.internalId).map(_.as[A]))

    def set[A: Writes](value: A)(implicit req: AuthorisedRequest[AnyContent]): Future[Unit] = {
      state = state + (req.internalId -> Json.toJson(value))
      Future.successful(())
    }
  }

  def index(id: String): Action[AnyContent] = authorisedAction.async { implicit request =>
    val hasCTEnrolment = request.enrolments.getEnrolment("IR-CT").isDefined
    cache.get(request.internalId) flatMap {
      case Some(fd) =>
        interpret(RegistrationControllerNew.journey(hasCTEnrolment, fd)).run(id) { subscription =>
          for {
            _ <- sdilConnector.submit(Subscription.desify(subscription), fd.rosmData.safeId)
            _ <- completedPersistence.set(subscription)
          } yield Redirect(routes.RegistrationControllerNew.completed)
        }
      case None =>
        println("nothing in cache") // TODO we should set the cache and redirect
        NotFound("").pure[Future]
    }
  }

  def completed(): Action[AnyContent] = authorisedAction.async { implicit request =>
    completedPersistence.get().map {
      case None               => NotFound("No subscription pending")
      case Some(subscription) => Ok(subscription.toString)
    }
  }

}

object RegistrationControllerNew {

  def journey(
    hasCTEnrolment: Boolean,
    fd: RegistrationFormData
  ) =
    for {

      orgType <- if (hasCTEnrolment) ask[OrganisationTypeSoleless]("organisation-type")
                else ask[OrganisationType]("organisation-type")
      _            <- end("partnerships") when (orgType.entryName == OrganisationType.partnership.entryName)
      producerType <- ask[ProducerType]("producer")
      packLarge = producerType match {
        case ProducerType.Large => Some(true)
        case ProducerType.Small => Some(false)
        case _                  => None
      }
      useCopacker <- ask[Boolean]("copacked") when producerType == ProducerType.Small
      packageOwn  <- askEmptyOption[(Long, Long)]("package-own-uk") emptyUnless packLarge.nonEmpty
      copacks     <- askEmptyOption[(Long, Long)]("package-copack")
      imports     <- askEmptyOption[(Long, Long)]("import")
      noUkActivity = (copacks, imports).isEmpty
      smallProducerWithNoCopacker = packLarge.forall(_ == false) && useCopacker.forall(_ == false)
      _ <- end("do-not-register") when (noUkActivity && smallProducerWithNoCopacker)
      isVoluntary = packLarge.contains(false) && useCopacker.contains(true) && (copacks, imports).isEmpty
      regDate <- ask[LocalDate]("start-date") unless isVoluntary
      askPackingSites = (packLarge.contains(true) && packageOwn.nonEmpty) || copacks.nonEmpty
      useBusinessAddress <- ask[Boolean]("pack-at-business-address") when askPackingSites
      packingSites = if (useBusinessAddress.getOrElse(false)) {
        List(Site.fromAddress(fd.rosmData.address))
      } else {
        List.empty[Site]
      }
      packSites <- askList[Site]("production-site-details") {
                    case (index: Option[Int], existing: List[Site]) =>
                      ask[Site]("site", default = index.map(existing))
                  } emptyUnless askPackingSites
      _ <- askList[Site]("sites") {
            case (index: Option[Int], existing: List[Site]) =>
              ask[Site]("site", default = index.map(existing))
          }
      addWarehouses <- isVoluntary match {
                        case true  => ask[Boolean]("ask-secondary-warehouses")
                        case false => pure(false)
                      }
      warehouses <- askList[Site](
                     "warehouses",
                     validation = Rule.nonEmpty
                   ) {
                     case (index: Option[Int], existing: List[Site]) =>
                       ask[Site]("site", default = index.map(existing))
                   } emptyUnless addWarehouses
      contactDetails <- ask[ContactDetails]("contact-details")
      activity = Activity(
        longTupToLitreage(packageOwn),
        longTupToLitreage(imports),
        longTupToLitreage(copacks),
        useCopacker.collect { case true => Litreage(1, 1) }, // TODO - why (1,1) ?
        packLarge.getOrElse(false)
      )
      declaration = Html("TODO declaration") // uniform.fragments.registerDeclarationNew(
      //   fd,
      //   packLarge,
      //   useCopacker,
      //   activity.ProducedOwnBrand,
      //   activity.CopackerAll,
      //   activity.Imported,
      //   isVoluntary,
      //   regDate,
      //   warehouses,
      //   packSites,
      //   contactDetails,
      //   implicitly[UniformMessages[Html]]
      // )
      _ <- tell("declaration", declaration)
    } yield
      Subscription(
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

}
