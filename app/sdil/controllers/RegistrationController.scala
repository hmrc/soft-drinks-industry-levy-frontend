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

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime, ZoneId}

import cats.implicits._
import enumeratum._
import ltbs.play.scaffold.GdsComponents._
import ltbs.play.scaffold.HtmlShow
import ltbs.play.scaffold.SdilComponents._
import ltbs.play.scaffold._
import ltbs.play.scaffold.webmonad._
import play.api.data.Form
import play.api.i18n.{Messages, MessagesApi}
import play.api.mvc.{Action, AnyContent, Result}
import play.twirl.api.Html
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


class RegistrationController(val messagesApi: MessagesApi,
                             authorisedAction: AuthorisedAction,
                             sdilConnector: SoftDrinksIndustryLevyConnector,
                             registeredAction: RegisteredAction,
                             cache: RegistrationFormDataCache
                            )(implicit
                              val config: AppConfig,
                              val ec: ExecutionContext
                            ) extends SdilWMController with FrontendController {


  def index(id: String): Action[AnyContent] = authorisedAction.async { implicit request =>
    val persistence = SaveForLaterPersistence("registration", request.internalId, cache.shortLiveCache)
    cache.get(request.internalId) flatMap {
      case Some(fd) => runInner(request)(program(request, fd))(id)(persistence.dataGet,persistence.dataPut)
      case None => NotFound("").pure[Future]
    }
  }

  // TODO - refactor data structures
  private def program(request: AuthorisedRequest[AnyContent], fd: RegistrationFormData)(implicit hc: HeaderCarrier): WebMonad[Result] = {

//    sealed trait Producer extends EnumEntry
//    object Producer extends Enum[Producer] {
//      val values = findValues
//      case object NonProducer extends Producer
//      case object LargeProducer extends Producer
//      case object SmallProducer extends Producer
//    }

    val hasCTEnrolment = request.enrolments.getEnrolment("IR-CT").isDefined
    val soleTrader = if (hasCTEnrolment) Nil else Seq("soleTrader")
    val litres = litreagePair.nonEmpty("error.litreage.zero")

    for {
      orgType        <- askOneOf("organisation-type", (orgTypes ++ soleTrader))
      noPartners     = uniform.fragments.partnerships()(request, implicitly, implicitly)
      _              <- if (orgType === "partnership") {
                         end("partners",noPartners)
                       } else (()).pure[WebMonad]
      packLarge       <- askOneOf("packLarge", producerTypes) map {
        case "large" => Some(true)
        case "small" => Some(false)
        case _ => None
      }
      useCopacker    <- ask(bool,"useCopacker") when packLarge.contains(false)
      packageOwn     <- askOption(litreagePair.nonEmpty, "own-brands-packaged-at-own-sites") when packLarge.nonEmpty
      copacks        <- askOption(litreagePair.nonEmpty, "packaged-as-a-contract-packer")
      imports        <- askOption(litreagePair.nonEmpty, "brought-into-uk")
      noUkActivity   =  (copacks, imports).isEmpty
      smallProducerWithNoCopacker =  packLarge.forall(_ == false) && useCopacker.forall(_ == false)
      shouldNotReg   =  noUkActivity && smallProducerWithNoCopacker
      noReg          = uniform.fragments.registration_not_required()(request, implicitly, implicitly)
      _              <- if (shouldNotReg) {
                          end("do-not-register",noReg)
                        } else (()).pure[WebMonad]
      regDate        <- ask(startDate
                              .verifying("error.start-date.in-future", !_.isAfter(LocalDate.now))
                              .verifying("error.start-date.before-tax-start", !_.isBefore(LocalDate.of(2018, 4, 6))), "regDate")
      packSites       <- askPackSites(List.empty[Site], (packLarge.contains(true) && packageOwn.contains(true)) || !copacks.isEmpty)
      isVoluntary     =  packLarge.contains(false) && useCopacker.contains(true) && (copacks, imports).isEmpty
      warehouses      <- manyT("warehousesActivity", ask(warehouseSiteMapping,_)(warehouseSiteForm, implicitly)) emptyUnless !isVoluntary
      contactDetails  <- ask(contactDetailsMapping, "contact")
      activity        = Activity(
        longTupToLitreage(packageOwn.flatten.getOrElse((0,0))),
        longTupToLitreage(imports.getOrElse((0,0))),
        longTupToLitreage(copacks.getOrElse((0,0))),
        useCopacker.collect { case true => Litreage(1, 1) },
        packLarge.getOrElse(false)
      )
      declaration     = uniform.fragments.declaration(
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
      contact         = Contact(
                          name = Some(contactDetails.fullName),
                          positionInCompany = Some(contactDetails.position),
                          phoneNumber = contactDetails.phoneNumber,
                          email = contactDetails.email
                        )
      subscription    = Subscription(
                          fd.utr,
                          fd.rosmData.organisationName,
                          orgType,
                          UkAddress.fromAddress(fd.rosmData.address),
                          activity,
                          regDate,
                          packSites,
                          warehouses,
                          contact
                        )
      _               <- execute(sdilConnector.submit(Subscription.desify(subscription), fd.rosmData.safeId))
      df              = DateTimeFormatter.ofPattern("d MMMM yyyy")
      tf              = DateTimeFormatter.ofPattern("h:mma")
      now             = LocalDateTime.now(ZoneId.of("Europe/London"))
      complete        = uniform.fragments.registrationComplete(contact.email, now.format(df), now.format(tf).toLowerCase, isVoluntary)(request, implicitly, implicitly)
      sh              = Messages("sdil.complete.subheading")
      end             <- clear >> journeyEnd("registration-complete", subheading = Html(sh).some, whatHappensNext = complete.some)
    } yield end
  }

}
