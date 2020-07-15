/*
 * Copyright 2020 HM Revenue & Customs
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
import ltbs.play.scaffold.SdilComponents.{extraMessages, litreageForm => approxLitreageForm, _}
import play.api.data.Mapping
import play.api.i18n._
import play.api.libs.json.Format
import play.api.mvc._
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
import uk.gov.hmrc.uniform.playutil._
import uk.gov.hmrc.uniform.webmonad._
import views.html.uniform
import views.uniform.Uniform

import scala.concurrent.{ExecutionContext, Future}

class RegistrationController(
  authorisedAction: AuthorisedAction,
  sdilConnector: SoftDrinksIndustryLevyConnector,
  registeredAction: RegisteredAction,
  cache: RegistrationFormDataCache,
  mcc: MessagesControllerComponents,
  uniformHelpers: Uniform,
  config: AppConfig)(override implicit val ec: ExecutionContext)
    extends SdilWMController(uniformHelpers, mcc, config) with I18nSupport {

  override implicit lazy val messages = MessagesImpl(mcc.langs.availables.head, messagesApi)
  override lazy val parse = mcc.parsers
  implicit val appConfig = config

  def index(id: String): Action[AnyContent] = authorisedAction.async { implicit request =>
    val persistence = SaveForLaterPersistence("registration", request.internalId, cache.shortLiveCache)
    cache.get(request.internalId) flatMap {
      case Some(fd) => runInner(request)(program(fd)(request, implicitly))(id)(persistence.dataGet, persistence.dataPut)
      case None     => NotFound("").pure[Future]
    }
  }

  private[controllers] def program(
    fd: RegistrationFormData)(implicit request: AuthorisedRequest[AnyContent], hc: HeaderCarrier): WebMonad[Result] = {

    val hasCTEnrolment = request.enrolments.getEnrolment("IR-CT").isDefined
    val organisationTypes = OrganisationType.values.toList
      .filterNot(_ == soleTrader && hasCTEnrolment)
      .sortBy(x => Messages("organisation-type.option." + x.toString.toLowerCase))
    implicit val extraMessages: ExtraMessages =
      ExtraMessages(
        messages = Map(
          "pack-at-business-address.lead" -> s"Registered address: ${fd.rosmData.address.nonEmptyLines.mkString(", ")}",
          "confirm-address.paragraph"     -> s"${fd.rosmData.address.nonEmptyLines.mkString(", ")}"
        )
      )

    for {
      orgType <- askOneOf("organisation-type", organisationTypes)
      noPartners = uniform.fragments.partnerships()
      _ <- if (orgType == partnership) {
            end("partnerships", noPartners)
          } else (()).pure[WebMonad]
      packLarge <- askOneOf("producer", ProducerType.values.toList) map {
                    case Large => Some(true)
                    case Small => Some(false)
                    case _     => None
                  }
      useCopacker <- ask(bool("copacked"), "copacked") when packLarge.contains(false)
      packageOwn <- askOption(litreagePair.nonEmpty, "package-own-uk")(
                     approxLitreageForm,
                     implicitly,
                     implicitly,
                     implicitly) when packLarge.nonEmpty
      copacks <- askOption(litreagePair.nonEmpty, "package-copack")(
                  approxLitreageForm,
                  implicitly,
                  implicitly,
                  implicitly)
      imports <- askOption(litreagePair.nonEmpty, "import")(approxLitreageForm, implicitly, implicitly, implicitly)
      noUkActivity = (copacks, imports).isEmpty
      smallProducerWithNoCopacker = packLarge.forall(_ == false) && useCopacker.forall(_ == false)
      noReg = uniform.fragments.registration_not_required()(request, implicitly, implicitly)
      _ <- if (noUkActivity && smallProducerWithNoCopacker) {
            end("do-not-register", noReg)
          } else (()).pure[WebMonad]
      isVoluntary = packLarge.contains(false) && useCopacker.contains(true) && (copacks, imports).isEmpty
      regDate <- askRegDate(packLarge, copacks, imports) when (!isVoluntary)
      askPackingSites = (packLarge.contains(true) && packageOwn.flatten.nonEmpty) || !copacks.isEmpty
      useBusinessAddress <- ask(bool("pack-at-business-address"), "pack-at-business-address") when askPackingSites
      packingSites = if (useBusinessAddress.getOrElse(false)) {
        List(Site.fromAddress(fd.rosmData.address))
      } else {
        List.empty[Site]
      }
      firstPackingSite <- ask(packagingSiteMapping, "first-production-site")(
                           packagingSiteForm,
                           implicitly,
                           ExtraMessages(),
                           implicitly) when packingSites.isEmpty && askPackingSites
      packSites     <- askPackSites(packingSites ++ firstPackingSite.fold(List.empty[Site])(x => List(x))) emptyUnless askPackingSites
      addWarehouses <- ask(bool("ask-secondary-warehouses"), "ask-secondary-warehouses") when !isVoluntary
      firstWarehouse <- ask(warehouseSiteMapping, "first-warehouse")(
                         warehouseSiteForm,
                         implicitly,
                         ExtraMessages(),
                         implicitly) when addWarehouses.getOrElse(false)
      warehouses <- askWarehouses(List.empty[Site] ++ firstWarehouse.fold(List.empty[Site])(x => List(x))) emptyUnless addWarehouses
                     .getOrElse(false)
      contactDetails <- ask(contactDetailsMapping, "contact-details")
      activity = Activity(
        longTupToLitreage(packageOwn.flatten.getOrElse((0, 0))),
        longTupToLitreage(imports.getOrElse((0, 0))),
        longTupToLitreage(copacks.getOrElse((0, 0))),
        useCopacker.collect { case true => Litreage(1, 1) },
        packLarge.getOrElse(false)
      )
      contact = Contact(
        name = Some(contactDetails.fullName),
        positionInCompany = Some(contactDetails.position),
        phoneNumber = contactDetails.phoneNumber,
        email = contactDetails.email
      )
      subscription = Subscription(
        fd.utr,
        fd.rosmData.organisationName,
        orgType.toString,
        UkAddress.fromAddress(fd.rosmData.address),
        activity,
        regDate.getOrElse(LocalDate.now),
        packSites,
        warehouses,
        contact
      )
      declaration = uniform.fragments.registerDeclaration(
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
      _ <- tell("declaration", declaration)(implicitly, ltbs.play.scaffold.SdilComponents.extraMessages)
      _ <- execute(sdilConnector.submit(Subscription.desify(subscription), fd.rosmData.safeId))
      _ <- execute(cache.clear(request.internalId))
      complete = uniform.fragments.registrationComplete(contact.email)
      subheading = Html(Messages("complete.subheading", subscription.orgName))
      extraEndMessages = ExtraMessages(
        Map("complete.extraParagraph" -> s"We have sent a confirmation email to ${contact.email}."))
      end <- clear >> journeyEnd("complete", whatHappensNext = complete.some, getTotal = subheading.some)(
              extraEndMessages)
    } yield end
  }
  private[controllers] def askRegDate(
    packLarge: Option[Boolean],
    copacks: Option[(Long, Long)],
    imports: Option[(Long, Long)]): WebMonad[LocalDate] = {
    def askRD[T](mapping: Mapping[T], key: String, helpText: Option[Html])(
      implicit htmlForm: FormHtml[T],
      fmt: Format[T],
      extraMessages: ExtraMessages): WebMonad[T] =
      formPage(key)(mapping, None) { (path, form, r) =>
        implicit val request: Request[AnyContent] = r
        uniformHelpers.ask(key, form, htmlForm.asHtmlForm(key, form), path, helpText)
      }
    askRD(
      startDate
        .verifying("error.start-date.in-future", !_.isAfter(LocalDate.now))
        .verifying("error.start-date.before-tax-start", !_.isBefore(LocalDate.of(2018, 4, 6))),
      "start-date",
      Some(uniform.fragments.startDateHelp(packLarge.getOrElse(false), copacks.isDefined, imports.isDefined))
    )
  }
}
