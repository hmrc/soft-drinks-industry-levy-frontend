/*
 * Copyright 2018 HM Revenue & Customs
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
import ltbs.play.scaffold.GdsComponents._
import ltbs.play.scaffold.SdilComponents.OrganisationType.{partnership, soleTrader}
import ltbs.play.scaffold.SdilComponents.ProducerType.{Large, Small}
import ltbs.play.scaffold.SdilComponents._
import ltbs.play.scaffold.webmonad._
import play.api.i18n.{Messages, MessagesApi}
import play.api.mvc.{Action, AnyContent, Result}
import sdil.actions.{AuthorisedAction, AuthorisedRequest, RegisteredAction}
import sdil.config.{AppConfig, RegistrationFormDataCache}
import sdil.connectors.SoftDrinksIndustryLevyConnector
import sdil.models.backend._
import sdil.models.{Litreage, RegistrationFormData}
import sdil.uniform.SaveForLaterPersistence
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import views.html.uniform

import scala.concurrent.{ExecutionContext, Future}


class RegistrationController(
                              val messagesApi: MessagesApi,
                              authorisedAction: AuthorisedAction,
                              sdilConnector: SoftDrinksIndustryLevyConnector,
                              registeredAction: RegisteredAction,
                              cache: RegistrationFormDataCache
                            )(
                              implicit
                              val config: AppConfig,
                              val ec: ExecutionContext
                            )
  extends SdilWMController with FrontendController {

  // TODO - when the old registration code is removed we can replace fd and create a Registration data structure
  def index(id: String): Action[AnyContent] = authorisedAction.async { implicit request =>
    val persistence = SaveForLaterPersistence("registration", request.internalId, cache.shortLiveCache)
    cache.get(request.internalId) flatMap {
      case Some(fd) => runInner(request)(program(fd)(request, implicitly))(id)(persistence.dataGet,persistence.dataPut)
      case None => NotFound("").pure[Future]
    }
  }

  private def askRegDate = {
    ask(
      startDate
        .verifying(
          "error.start-date.in-future",
          !_.isAfter(LocalDate.now)
        ).verifying(
        "error.start-date.before-tax-start",
        !_.isBefore(LocalDate.of(2018, 4, 6))),
      "start-date"
    )
  }

  private def askWarehouses = {
    manyT("warehousesActivity", ask(warehouseSiteMapping,_)(warehouseSiteForm, implicitly), editSingleForm = Some((warehouseSiteMapping, warehouseSiteForm)))
  }

  private def program(fd: RegistrationFormData)
                     (implicit request: AuthorisedRequest[AnyContent], hc: HeaderCarrier): WebMonad[Result] = {

    val hasCTEnrolment = request.enrolments.getEnrolment("IR-CT").isDefined
    val organisationTypes = OrganisationType.values.toList
      .filterNot(_== soleTrader && hasCTEnrolment)
      .sortBy(x => Messages("organisation-type.option." + x.toString.toLowerCase))



    for {
      orgType        <- askOneOf("organisation-type", organisationTypes)
      noPartners     =  uniform.fragments.partnerships
      _              <- if (orgType == partnership) {
                          end("partners",noPartners)
                        } else (()).pure[WebMonad]
      packLarge       <- askOneOf("producer", ProducerType.values.toList) map {
                          case Large => Some(true)
                          case Small => Some(false)
                          case _ => None
                        }
      useCopacker    <- ask(bool,"copacked") when packLarge.contains(false)
      packageOwn     <- askOption(litreagePair.nonEmpty, "package-own-uk") when packLarge.nonEmpty
      copacks        <- askOption(litreagePair.nonEmpty, "package-copack")
      imports        <- askOption(litreagePair.nonEmpty, "import")
      noUkActivity   =  (copacks, imports).isEmpty
      smallProducerWithNoCopacker =  packLarge.forall(_ == false) && useCopacker.forall(_ == false)
      noReg          =  uniform.fragments.registration_not_required()(request, implicitly, implicitly)
      _              <- if (noUkActivity && smallProducerWithNoCopacker) {
                          end("do-not-register",noReg)
                        } else (()).pure[WebMonad]
      regDate        <- askRegDate
      packSites      <- askPackSites(
                          List.empty[Site]) emptyUnless
                          (packLarge.contains(true) && packageOwn.contains(true)) || !copacks.isEmpty
      isVoluntary     =  packLarge.contains(false) && useCopacker.contains(true) && (copacks, imports).isEmpty
      warehouses      <- askWarehouses emptyUnless !isVoluntary
      contactDetails  <- ask(contactDetailsMapping, "contact-details")
      activity        =  Activity(
                           longTupToLitreage(packageOwn.flatten.getOrElse((0,0))),
                           longTupToLitreage(imports.getOrElse((0,0))),
                           longTupToLitreage(copacks.getOrElse((0,0))),
                           useCopacker.collect { case true => Litreage(1, 1) },
                           packLarge.getOrElse(false)
                         )
      contact         =  Contact(
                           name = Some(contactDetails.fullName),
                           positionInCompany = Some(contactDetails.position),
                           phoneNumber = contactDetails.phoneNumber,
                           email = contactDetails.email
                         )
      subscription    =  Subscription(
                           fd.utr,
                           fd.rosmData.organisationName,
                           orgType.toString,
                           UkAddress.fromAddress(fd.rosmData.address),
                           activity,
                           regDate,
                           packSites,
                           warehouses,
                           contact
                         )
      declaration     =  uniform.fragments.registerDeclaration(
                           fd,
                           packLarge,
                           useCopacker,
                           activity.ProducedOwnBrand,
                           activity.CopackerAll,
                           activity.Imported,
                           isVoluntary,
                           regDate,
                           warehouses,
                           packSites,
                           contactDetails
                         )(request, implicitly, implicitly)
      _               <- tell("registerDeclaration", declaration)
      _               <- execute(sdilConnector.submit(Subscription.desify(subscription), fd.rosmData.safeId))
      _               <- execute(cache.clear(request.internalId))
      complete        =  uniform.fragments.registrationComplete(contact.email)
      end             <- clear >> journeyEnd("complete", whatHappensNext = complete.some)
    } yield end
  }

}
