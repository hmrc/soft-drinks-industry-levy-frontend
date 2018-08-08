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

import cats.implicits._
import uk.gov.hmrc.uniform._
import uk.gov.hmrc.uniform.webmonad._
import uk.gov.hmrc.uniform.playutil._
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.Mapping
import play.api.i18n.{Messages, MessagesApi}
import play.api.libs.json._
import play.api.mvc._
import play.twirl.api.Html
import sdil.actions.RegisteredAction
import sdil.config._
import sdil.connectors.SoftDrinksIndustryLevyConnector
import sdil.models._
import sdil.models.backend.Activity
import sdil.models.retrieved.{RetrievedSubscription => Subscription}
import sdil.models.variations.{SdilActivity, VariationData, VariationsSubmission}
import sdil.uniform._
import uk.gov.hmrc.domain.Modulus23Check
import uk.gov.hmrc.http.cache.client.ShortLivedHttpCaching
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import views.html.uniform
import ltbs.play.scaffold.GdsComponents._
import ltbs.play.scaffold.SdilComponents._

import scala.concurrent._
import scala.concurrent.duration._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import uk.gov.hmrc.http.HeaderCarrier
import java.time._
import java.time.format._

import sdil.models.backend.Site

class ReturnsController (
  val messagesApi: MessagesApi,
  sdilConnector: SoftDrinksIndustryLevyConnector,
  registeredAction: RegisteredAction,
  cache: ShortLivedHttpCaching
)(implicit
  val config: AppConfig,
  val ec: ExecutionContext
) extends SdilWMController with FrontendController with Modulus23Check {

  //TODO extract to config
  val costLower = BigDecimal("0.18")
  val costHigher = BigDecimal("0.24")

  implicit val address: Format[SmallProducer] = Json.format[SmallProducer]

  implicit val litreageForm = new FormHtml[(Long,Long)] {
    def asHtmlForm(key: String, form: Form[(Long,Long)])(implicit messages: Messages): Html = {
      uniform.fragments.litreage(key, form, false)(messages)
    }
  }

  implicit val smallProducerHtml: HtmlShow[SmallProducer] =
      HtmlShow.instance { producer =>
        Html(producer.alias.map { x =>
          "<h3>" ++ Messages("small-producer-details.name", x) ++"<br/>"
        }.getOrElse(
          "<h3>"
        ) 
          ++ Messages("small-producer-details.refNumber", producer.sdilRef) ++ "</h3>"
          ++ "<br/>"
          ++ Messages("small-producer-details.lowBand", f"${producer.litreage._1}%,d")
          ++ "<br/>"
          ++ Messages("small-producer-details.highBand", f"${producer.litreage._2}%,d")
        )
      }

  // TODO: At present this uses an Await.result to check the small producer status, thus
  // blocking a thread. At a later date uniform should be updated to include the capability
  // for a subsequent stage to invalidate a prior one.
  implicit def smallProducer(implicit hc: HeaderCarrier): Mapping[SmallProducer] = mapping(
    "alias" -> optional(text),
    "sdilRef" -> nonEmptyText
      .verifying(
        "error.sdilref.invalid", x => {
          x.isEmpty ||
            (x.matches("^X[A-Z]SDIL000[0-9]{6}$") &&
            isCheckCorrect(x, 1) &&
            Await.result(isSmallProducer(x), 20.seconds))
        }),
    "lower"   -> litreage,
    "higher"  -> litreage
  ){
    (alias, ref,l,h) => SmallProducer(alias, ref, (l,h))
  }{
    case SmallProducer(alias, ref, (l,h)) => (alias, ref,l,h).some
  }

  def returnAmount(sdilReturn: SdilReturn) = {
    List(
      ("own-brands-packaged-at-own-sites", sdilReturn.ownBrand, 1),
      ("packaged-as-a-contract-packer", sdilReturn.packLarge, 1),
      ("exemptions-for-small-producers", sdilReturn.packSmall.map{_.litreage}.combineAll, 0),
      ("brought-into-uk", sdilReturn.importLarge, 1),
      ("brought-into-uk-from-small-producers", sdilReturn.importSmall, 0),
      ("claim-credits-for-exports", sdilReturn.export, -1),
      ("claim-credits-for-lost-damaged", sdilReturn.wastage, -1)
    )
  }

  private def calculateSubtotal(d: List[(String, (Long, Long), Int)]): BigDecimal = {
    d.map{case (_, (l,h), m) => costLower * l * m + costHigher * h * m}.sum
  }

  def checkYourAnswers(key: String, sdilReturn: SdilReturn, broughtForward: BigDecimal, variation: ReturnsVariation): WebMonad[Unit] = {

    val data = returnAmount(sdilReturn)
    val subtotal = calculateSubtotal(data)
    val total: BigDecimal = subtotal + broughtForward

    val inner = uniform.fragments.returnsCYA(
      key,
      data,
      costLower,
      costHigher,
      subtotal,
      broughtForward,
      total,
      variation)
    tell(key, inner)
  }

  private def confirmationPage(key: String, period: ReturnPeriod, subscription: Subscription,
                               sdilReturn: SdilReturn, broughtForward: BigDecimal, sdilRef: String)(implicit messages: Messages): WebMonad[Result] = {
    val now = LocalDate.now
    import ltbs.play._

    val data = returnAmount(sdilReturn)
    val subtotal = calculateSubtotal(data)

    val total = subtotal + broughtForward

    def formatMoney (total: BigDecimal) = {
      if(total < 0)
        f"-£${total.abs}%,.2f"
       else
        f"£$total%,.2f"
      }

    val getTotal =
      if (total == 0)
        messages("return-sent.subheading.nil-return")
      else
        messages("return-sent.subheading", formatMoney(total), period.deadline.format("dd MMMM yyyy"))

    val returnDate = messages(
      "return-sent.returnsDoneMessage",
      period.start.format("MMMM"),
      period.end.format("MMMM"),
      period.start.getYear.toString,
      subscription.orgName,
      LocalTime.now(ZoneId.of("Europe/London")).format(DateTimeFormatter.ofPattern("h:mma")).toLowerCase,
      now.format("dd MMMM yyyy")
    )

    val whatHappensNext = uniform.fragments.returnsPaymentsBlurb(period, sdilRef, total)(messages).some
    journeyEnd(key, now, Html(returnDate).some, whatHappensNext, Html(getTotal).some)
  }


  private def askReturn(implicit hc: HeaderCarrier): WebMonad[SdilReturn] = for {
    ownBrands      <- askEmptyOption(litreagePair, "own-brands-packaged-at-own-sites")
    contractPacked <- askEmptyOption(litreagePair, "packaged-as-a-contract-packer")
    askSmallProd   <- ask(bool, "exemptions-for-small-producers")
    firstSmallProd <- ask(smallProducer, "first-small-producer-details") when askSmallProd
    smallProds     <- manyT("small-producer-details",
                            {ask(smallProducer, _)},
                            min = 1,
                            default = firstSmallProd.fold(List.empty[SmallProducer])(x => List(x)),
                            editSingleForm = Some((smallProducer, smallProducerForm))
                           ) when askSmallProd
    imports        <- askEmptyOption(litreagePair, "brought-into-uk")
    importsSmall   <- askEmptyOption(litreagePair, "brought-into-uk-from-small-producers")
    exportCredits  <- askEmptyOption(litreagePair, "claim-credits-for-exports")
    wastage        <- askEmptyOption(litreagePair, "claim-credits-for-lost-damaged")
    sdilReturn     =  SdilReturn(ownBrands,contractPacked,smallProds.getOrElse(Nil),imports,importsSmall,exportCredits,wastage)
  } yield sdilReturn

  implicit class LongComparison(x :(Long,Long)) {
    def + (y:(Long,Long)): (Long,Long) = {
      (x._1 + y._1, x._2 + y._2)
    }
    def > (y:Long): Boolean = {
        x._1 + x._2 > y
    }
  }

  private def askNewWarehouses()(implicit hc: HeaderCarrier): WebMonad[List[Site]] = for {
    addWarehouses  <- ask(bool, "ask-secondary-warehouses-in-return")(implicitly, implicitly, extraMessages)
    firstWarehouse <- ask(warehouseSiteMapping,"first-warehouse")(warehouseSiteForm, implicitly, ExtraMessages()) when
      addWarehouses
    warehouses     <- askWarehouses(firstWarehouse.fold(List.empty[Site])(x => List(x))) emptyUnless
      addWarehouses
  } yield warehouses

  private def askNewPackingSites(subscription: Subscription)(implicit hc: HeaderCarrier): WebMonad[List[Site]] = {
    val extraMessages  = ExtraMessages(
      messages = Map(
        "pack-at-business-address-in-return.lead" -> s"Registered address: ${Address.fromUkAddress(subscription.address).nonEmptyLines.mkString(", ")}")
    )
    for {
      usePPOBAddress   <- ask(bool, "pack-at-business-address-in-return")(implicitly, implicitly, extraMessages)
      packingSites     = if (usePPOBAddress) {
        List(Site.fromAddress(Address.fromUkAddress(subscription.address)))
      } else {
        List.empty[Site]
      }
      firstPackingSite <- ask(packagingSiteMapping,"first-production-site")(packagingSiteForm, implicitly, ExtraMessages()) when
        packingSites.isEmpty
      packingSites     <- askPackSites(packingSites ++ firstPackingSite.fold(List.empty[Site])(x => List(x)))
    } yield packingSites
  }

  private def program(period: ReturnPeriod, subscription: Subscription, sdilRef: String)
                     (implicit hc: HeaderCarrier): WebMonad[Result] = for {
    sdilReturn     <- askReturn
    // check if they need to vary
    isNewImporter  = (sdilReturn.importLarge + sdilReturn.importSmall) > 0 && !subscription.activity.importer
    isNewPacker    = sdilReturn.packLarge > 0 && !subscription.activity.contractPacker
    inner          = uniform.fragments.return_variation_continue(isNewImporter, isNewPacker)
    _              <- tell("return-change-registration", inner) when isNewImporter || isNewPacker
    newWarehouses     <- askNewWarehouses when isNewImporter && subscription.warehouseSites.isEmpty
    newPackingSites <- askNewPackingSites(subscription) when isNewPacker // TODO uncomment && subscription.productionSites.isEmpty

    variation     = ReturnsVariation(
      orgName = subscription.orgName,
      importer = (isNewImporter, sdilReturn.importLarge + sdilReturn.importSmall),
      packer = (isNewPacker, sdilReturn.packLarge),
      warehouses = newWarehouses.getOrElse(List.empty[Site]),
      packingSites = newPackingSites.getOrElse(List.empty[Site]),
      phoneNumber = subscription.contact.phoneNumber,
      email = subscription.contact.email,
      taxEstimation = taxEstimation(sdilReturn)
    )

    broughtForward <- BigDecimal("0").pure[WebMonad]
    _              <- checkYourAnswers("check-your-answers", sdilReturn, broughtForward, variation)
    _              <- cachedFuture(s"return-${period.count}")(
                        sdilConnector.returns(subscription.utr, period) = sdilReturn)
    _              <- if (isNewImporter || isNewPacker) {
                        execute(sdilConnector.returns.variation(variation, sdilRef))
                      } else {
                        (()).pure[WebMonad]
                      }
    end            <- clear >> confirmationPage("return-sent", period, subscription, sdilReturn, broughtForward, sdilRef)
  } yield end

  def index(year: Int, quarter: Int, id: String): Action[AnyContent] = registeredAction.async { implicit request =>
    if (!config.returnsEnabled)
      throw new NotImplementedError("Returns are not enabled")
    val sdilRef = request.sdilEnrolment.value
    val period = ReturnPeriod(year, quarter)
    val persistence = SaveForLaterPersistence("variations", sdilRef, cache)

    for {
      subscription <- sdilConnector.retrieveSubscription(sdilRef).map{_.get}
      pendingReturns <- sdilConnector.returns.pending(subscription.utr)
      r   <- if (pendingReturns.contains(period))
               runInner(request)(program(period, subscription, sdilRef))(id)(persistence.dataGet,persistence.dataPut)
             else
               Redirect(routes.ServicePageController.show()).pure[Future]
    } yield r
  }

  def isSmallProducer(sdilRef: String)(implicit hc: HeaderCarrier): Future[Boolean] = 
    sdilConnector.retrieveSubscription(sdilRef).flatMap {
      case Some(x) => x.activity.smallProducer
      case None    => false
    }

  def taxEstimation(r: SdilReturn): BigDecimal = {
    val t = r.packLarge + r.importLarge + r.ownBrand
    (t._1 * costLower + t._2 * costHigher) * 4
  }

}
