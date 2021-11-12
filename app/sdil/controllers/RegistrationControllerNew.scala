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

import java.time.LocalDate

import cats.implicits._
import ltbs.play.scaffold.GdsComponents.bool
import ltbs.play.scaffold.SdilComponents.{OrganisationType, OrganisationTypeSoleless, ProducerType, contactDetailsMapping, longTupToLitreage, packagingSiteForm, packagingSiteMapping, warehouseSiteForm, warehouseSiteMapping}

import scala.language.higherKinds
import ltbs.uniform._
import ltbs.uniform.interpreters.playframework._
import play.api.i18n._
import play.api.mvc._
import play.twirl.api.Html
import sdil.actions.{AuthorisedAction, AuthorisedRequest}
import sdil.config.{AppConfig, RegistrationFormDataCache}
import sdil.connectors.SoftDrinksIndustryLevyConnector
import sdil.models.{Litreage, RegistrationFormData}
import sdil.models.backend._
import sdil.uniform.SaveForLaterPersistenceNew
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import uk.gov.hmrc.uniform.playutil.ExtraMessages
import uk.gov.hmrc.uniform.webmonad.clear
import views.html.uniform

import scala.concurrent.{ExecutionContext, Future}

class RegistrationControllerNew(
  authorisedAction: AuthorisedAction,
  sdilConnector: SoftDrinksIndustryLevyConnector,
  cache: RegistrationFormDataCache,
  mcc: MessagesControllerComponents,
  val config: AppConfig,
  val ufViews: views.uniform.Uniform,
  implicit val ec: ExecutionContext
) extends FrontendController(mcc) with I18nSupport with HmrcPlayInterpreter {

  def index(id: String): Action[AnyContent] = authorisedAction.async { implicit request =>
    implicit lazy val persistence: PersistenceEngine[AuthorisedRequest[AnyContent]] =
      SaveForLaterPersistenceNew("registration", request.internalId, cache.shortLiveCache)
    val hasCTEnrolment = request.enrolments.getEnrolment("IR-CT").isDefined
    cache.get(request.internalId) flatMap {
      case Some(fd) =>
        interpret(RegistrationControllerNew.journey(hasCTEnrolment, fd)).run(id) { _ =>
          Future.successful(Ok("hiya"))
        }
      case None =>
        println("nothing in cache") // TODO remove
        NotFound("").pure[Future]
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
      _ <- end("partnerships") when (orgType.entryName == OrganisationType.partnership.entryName)
      packLarge <- ask[ProducerType]("producer") map {
                    case ProducerType.Large => Some(true)
                    case ProducerType.Small => Some(false)
                    case _                  => None
                  }
      useCopacker <- ask[Boolean]("copacked") when packLarge.contains(false)
      packageOwn  <- ask[Some[(Long, Long)]]("package-own-uk") when packLarge.nonEmpty
      copacks     <- ask[Some[(Long, Long)]]("package-copack")
      imports     <- ask[Some[(Long, Long)]]("import")
      noUkActivity = (copacks, imports).isEmpty
      smallProducerWithNoCopacker = packLarge.forall(_ == false) && useCopacker.forall(_ == false)
      _ <- end("do-not-register") when (noUkActivity && smallProducerWithNoCopacker)
      isVoluntary = packLarge.contains(false) && useCopacker.contains(true) && (copacks, imports).isEmpty
      regDate <- ask[LocalDate]("start-date") unless isVoluntary
      askPackingSites = (packLarge.contains(true) && packageOwn.flatten.nonEmpty) || copacks.isDefined
      useBusinessAddress <- ask[Boolean]("pack-at-business-address") when askPackingSites
      packingSites = if (useBusinessAddress.getOrElse(false)) {
        List(Site.fromAddress(fd.rosmData.address))
      } else {
        List.empty[Site]
      }
      firstPackingSite <- ask[Site]("first-production-site") when packingSites.isEmpty && askPackingSites

      packSites <- askList[Site]("production-site-details") {
                    case (index: Option[Int], existing: List[Site]) =>
                      ask[Site]("site", default = index.map(existing))
                  } emptyUnless askPackingSites

//                    packingSites ++ firstPackingSite
//                      .fold(List.empty[Site])(x => List(x))
//                      .some
//                  } emptyUnless askPackingSites

      _ <- askList[Site]("sites") {
            case (index: Option[Int], existing: List[Site]) =>
              ask[Site]("site", default = index.map(existing))
          } emptyUnless true

      //      addWarehouses <- ask(bool("ask-secondary-warehouses"), "ask-secondary-warehouses") when !isVoluntary
//      firstWarehouse <- ask(warehouseSiteMapping, "first-warehouse")(
//        warehouseSiteForm,
//        implicitly,
//        ExtraMessages(),
//        implicitly) when addWarehouses.getOrElse(false)
//      warehouses <- askWarehouses(List.empty[Site] ++ firstWarehouse.fold(List.empty[Site])(x => List(x))) emptyUnless addWarehouses
//        .getOrElse(false)
//      contactDetails <- ask(contactDetailsMapping, "contact-details")
//      activity = Activity(
//        longTupToLitreage(packageOwn.flatten.getOrElse((0, 0))),
//        longTupToLitreage(imports.getOrElse((0, 0))),
//        longTupToLitreage(copacks.getOrElse((0, 0))),
//        useCopacker.collect { case true => Litreage(1, 1) },
//        packLarge.getOrElse(false)
//      )
//      contact = Contact(
//        name = Some(contactDetails.fullName),
//        positionInCompany = Some(contactDetails.position),
//        phoneNumber = contactDetails.phoneNumber,
//        email = contactDetails.email
//      )
//      subscription = Subscription(
//        fd.utr,
//        fd.rosmData.organisationName,
//        orgType.toString,
//        UkAddress.fromAddress(fd.rosmData.address),
//        activity,
//        regDate.getOrElse(LocalDate.now),
//        packSites,
//        warehouses,
//        contact
//      )
//      declaration = uniform.fragments.registerDeclaration(
//        fd,
//        packLarge,
//        useCopacker,
//        activity.ProducedOwnBrand,
//        activity.CopackerAll,
//        activity.Imported,
//        isVoluntary,
//        regDate,
//        warehouses,
//        packSites,
//        contactDetails
//      )(implicitly)
//      _ <- tell("declaration", declaration)(implicitly, ltbs.play.scaffold.SdilComponents.extraMessages)
//      _ <- execute(sdilConnector.submit(Subscription.desify(subscription), fd.rosmData.safeId))
//      _ <- execute(cache.clear(request.internalId))
//      complete = uniform.fragments.registrationComplete(contact.email)
//      subheading = Html(Messages("complete.subheading", subscription.orgName))
//      extraEndMessages = ExtraMessages(
//        Map("complete.extraParagraph" -> s"We have sent a confirmation email to ${contact.email}."))
//      end <- clear >> journeyEnd("complete", whatHappensNext = complete.some, getTotal = subheading.some)(
//        extraEndMessages)

      _ <- ask[LocalDate]("firstpage")
      _ <- askList[Site]("sites") {
            case (index: Option[Int], existing: List[Site]) =>
              ask[Site]("site", default = index.map(existing))
          } emptyUnless true
//      _                  <- interact[SmallProducer]("sndpage", Html("test"))
      _ <- ask[Either[Boolean, Int]]("thirdpage")
      _ <- ask[Int]("simple")
    } yield ()

}
