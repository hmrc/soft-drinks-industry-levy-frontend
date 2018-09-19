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
import play.api.data.Mapping
import uk.gov.hmrc.uniform.webmonad._
import uk.gov.hmrc.uniform.playutil._
import play.api.i18n.{Messages, MessagesApi}
import play.api.libs.json.Format
import play.api.mvc.{Action, AnyContent, Request, Result}
import play.twirl.api.Html
import sdil.actions.{AuthorisedAction, AuthorisedRequest, RegisteredAction}
import sdil.config.{AppConfig, RegistrationFormDataCache}
import sdil.connectors.SoftDrinksIndustryLevyConnector
import sdil.models.backend._
import sdil.models.{Litreage, RegistrationFormData}
import sdil.uniform.SaveForLaterPersistence
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import uk.gov.hmrc.uniform.FormHtml
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

  private def program(fd: RegistrationFormData)
                     (implicit request: AuthorisedRequest[AnyContent], hc: HeaderCarrier): WebMonad[Result] = {

    val hasCTEnrolment = request.enrolments.getEnrolment("IR-CT").isDefined
    val organisationTypes = OrganisationType.values.toList
      .filterNot(_== soleTrader && hasCTEnrolment)
      .sortBy(x => Messages("organisation-type.option." + x.toString.toLowerCase))

    for {
      orgType        <- askOneOf("organisation-type", organisationTypes)
      noPartners     =  uniform.fragments.partnerships()
      _              <- if (orgType == partnership) {
                          end("partnerships", noPartners)
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
      isVoluntary     =  packLarge.contains(false) && useCopacker.contains(true) && (copacks, imports).isEmpty
      regDate        <- askRegDate(packLarge, copacks, imports) when (!isVoluntary)
      askPackingSites = (packLarge.contains(true) && packageOwn.flatten.nonEmpty) || !copacks.isEmpty
      extraMessages   = ExtraMessages(messages = Map("pack-at-business-address.lead" -> s"Registered address: ${fd.rosmData.address.nonEmptyLines.mkString(", ")}"))
      useBusinessAddress <- ask(bool, "pack-at-business-address")(implicitly, implicitly, extraMessages) when askPackingSites
        packingSites   = if (useBusinessAddress.getOrElse(false)) {
                          List(Site.fromAddress(fd.rosmData.address))
                         } else {
                          List.empty[Site]
                         }
      firstPackingSite <- ask(packagingSiteMapping,"first-production-site")(packagingSiteForm, implicitly, ExtraMessages()) when packingSites.isEmpty && askPackingSites
      packSites       <- askPackSites(packingSites ++ firstPackingSite.fold(List.empty[Site])(x => List(x))) emptyUnless askPackingSites
      addWarehouses   <- ask(bool, "ask-secondary-warehouses")(implicitly, implicitly, extraMessages) when !isVoluntary
      firstWarehouse  <- ask(warehouseSiteMapping,"first-warehouse")(warehouseSiteForm, implicitly, ExtraMessages()) when addWarehouses.getOrElse(false)
      warehouses      <- askWarehouses(List.empty[Site] ++ firstWarehouse.fold(List.empty[Site])(x => List(x))) emptyUnless addWarehouses.getOrElse(false)
      contactDetails  <- ask(contactDetailsMapping, "contact-details")(implicitly, implicitly, ExtraMessages())
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
                           //TODO in refactor make start date non-optional & default to now.
                           regDate.getOrElse(LocalDate.now),
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
      _               <- tell("declaration", declaration)
      _               <- execute(sdilConnector.submit(Subscription.desify(subscription), fd.rosmData.safeId))
      _               <- execute(cache.clear(request.internalId))
      complete        =  uniform.fragments.registrationComplete(contact.email)
      end             <- clear >> journeyEnd("complete", whatHappensNext = complete.some)
    } yield end
  }
  private def askRegDate(packLarge: Option[Boolean], copacks: Option[(Long, Long)], imports: Option[(Long, Long)]): WebMonad[LocalDate] = {
    def askRD[T](mapping: Mapping[T], key: String, default: Option[T] = None, helpText: Option[Html])(implicit htmlForm: FormHtml[T], fmt: Format[T], extraMessages: ExtraMessages): WebMonad[T] =
      formPage(key)(mapping, default) { (path, form, r) =>
        implicit val request: Request[AnyContent] = r
        uniform.ask(key, form, htmlForm.asHtmlForm(key, form), path, helpText)
      }
    askRD(startDate
      .verifying("error.start-date.in-future", !_.isAfter(LocalDate.now))
      .verifying("error.start-date.before-tax-start", !_.isBefore(LocalDate.of(2018, 4, 6))),
      "start-date",
      None,
      Some(uniform.fragments.startDateHelp(packLarge.getOrElse(false), copacks.isDefined, imports.isDefined)))
  }
}
