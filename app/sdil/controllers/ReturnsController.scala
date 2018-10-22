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

import java.time._
import java.time.format._

import cats.implicits._
import ltbs.play.scaffold.GdsComponents._
import ltbs.play.scaffold.SdilComponents._
import play.api.i18n.{Messages, MessagesApi}
import play.api.mvc.{AnyContent, _}
import play.twirl.api.Html
import sdil.actions.RegisteredAction
import sdil.config._
import sdil.connectors.SoftDrinksIndustryLevyConnector
import sdil.models._
import sdil.models.backend.Site
import sdil.models.retrieved.{RetrievedSubscription => Subscription}
import sdil.uniform._
import uk.gov.hmrc.domain.Modulus23Check
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.ShortLivedHttpCaching
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import uk.gov.hmrc.uniform.playutil._
import uk.gov.hmrc.uniform.webmonad._
import views.html.uniform

import scala.concurrent._

class ReturnsController (
  val messagesApi: MessagesApi,
  sdilConnector: SoftDrinksIndustryLevyConnector,
  registeredAction: RegisteredAction,
  cache: ShortLivedHttpCaching
)(implicit
  val config: AppConfig,
  val ec: ExecutionContext
) extends SdilWMController with FrontendController with Modulus23Check with ReturnJourney {
  
  def confirmationPage(
    key: String,
    period: ReturnPeriod,
    subscription: Subscription,
    sdilReturn: SdilReturn,
    broughtForward: BigDecimal,
    sdilRef: String,
    isSmallProducer: Boolean,
    variation: ReturnsVariation)(implicit messages: Messages): WebMonad[Result] = {

    val now = LocalDate.now
    val data = returnAmount(sdilReturn, isSmallProducer)
    val subtotal = calculateSubtotal(data)

    val total = subtotal - broughtForward

    def formatMoney (total: BigDecimal) = {
      if(total < 0)
        f"-£${total.abs}%,.2f"
       else
        f"£$total%,.2f"
      }

    val getTotal =
      if (total <= 0 )
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

    val whatHappensNext = uniform.fragments.returnsPaymentsBlurb(
      period,
      sdilRef,
      total,
      formatMoney(total),
      variation,
      data,
      costLower,
      costHigher,
      subtotal,
      broughtForward)(messages).some

    journeyEnd(key, now, Html(returnDate).some, whatHappensNext, Html(getTotal).some)
  }

  private def askNewWarehouses()(implicit hc: HeaderCarrier): WebMonad[List[Site]] = for {
    addWarehouses  <- ask(bool, "ask-secondary-warehouses-in-return")
    firstWarehouse <- ask(warehouseSiteMapping,"first-warehouse")(warehouseSiteForm, implicitly, extraMessages, implicitly) when
      addWarehouses
    warehouses     <- askWarehouses(firstWarehouse.fold(List.empty[Site])(x => List(x))) emptyUnless
      addWarehouses
  } yield warehouses

  private def askNewPackingSites(subscription: Subscription)(implicit hc: HeaderCarrier): WebMonad[List[Site]] = {
    implicit val extraMessages  = ExtraMessages(
      messages = Map(
        "pack-at-business-address-in-return.lead" -> s"${Address.fromUkAddress(subscription.address).nonEmptyLines.mkString("<br/>")}")
    )
    for {
      usePPOBAddress   <- ask(bool, "pack-at-business-address-in-return")
      packingSites     = if (usePPOBAddress) {
        List(Site.fromAddress(Address.fromUkAddress(subscription.address)))
      } else {
        List.empty[Site]
      }
      firstPackingSite <- ask(packagingSiteMapping,"first-production-site")(packagingSiteForm, implicitly, implicitly, implicitly) when
        packingSites.isEmpty
      packingSites     <- askPackSites(packingSites ++ firstPackingSite.fold(List.empty[Site])(x => List(x)))
    } yield packingSites
  }

  private def program(period: ReturnPeriod, subscription: Subscription, sdilRef: String)
                     (implicit hc: HeaderCarrier): WebMonad[Result] = {
    val em = ExtraMessages(Map(
      "heading.check-your-answers" ->
        s"<span class='govuk-caption-xl'>${Messages(s"period.check-your-answers", period.start.format("MMMM"), period.end.format("MMMM yyyy"))}</span>${Messages("heading.check-your-answers")}"
      )
    )
    for {
      _ <- write[Boolean]("_editSmallProducers", true)
      sdilReturn <- askReturn(subscription, sdilRef, sdilConnector)
      // check if they need to vary
      isNewImporter = !sdilReturn.totalImported.isEmpty && !subscription.activity.importer
      isNewPacker = !sdilReturn.totalPacked.isEmpty && !subscription.activity.contractPacker
      inner = uniform.fragments.return_variation_continue(isNewImporter, isNewPacker)
      _ <- tell("return-change-registration", inner) when isNewImporter || isNewPacker
      newPackingSites <- askNewPackingSites(subscription) when isNewPacker && subscription.productionSites.isEmpty
      newWarehouses <- askNewWarehouses when isNewImporter && subscription.warehouseSites.isEmpty
      broughtForward <- if(config.balanceAllEnabled)
        execute(sdilConnector.balanceHistory(sdilRef, withAssessment = false).map { x =>
          extractTotal(listItemsWithTotal(x))
        })
      else
        execute(sdilConnector.balance(sdilRef, withAssessment = true))

      variation = ReturnsVariation(
        orgName = subscription.orgName,
        ppobAddress = subscription.address,
        importer = (isNewImporter, (sdilReturn.totalImported).combineN(4)),
        packer = (isNewPacker, (sdilReturn.totalPacked).combineN(4)),
        warehouses = newWarehouses.getOrElse(List.empty[Site]),
        packingSites = newPackingSites.getOrElse(List.empty[Site]),
        phoneNumber = subscription.contact.phoneNumber,
        email = subscription.contact.email,
        taxEstimation = taxEstimation(sdilReturn)
      )
      _ <- checkYourReturnAnswers("check-your-answers", sdilReturn, broughtForward, subscription, Some(variation))(em, implicitly)
      _ <- cachedFuture(s"return-${period.count}")(
        sdilConnector.returns(subscription.utr, period) = sdilReturn)
      _ <- if (isNewImporter || isNewPacker) {
        execute(sdilConnector.returns.variation(variation, sdilRef))
      } else {
        (()).pure[WebMonad]
      }
      end <- clear >> confirmationPage(
        "return-sent",
        period,
        subscription,
        sdilReturn,
        broughtForward,
        sdilRef,
        subscription.activity.smallProducer,
        variation
      )
    } yield end
  }
  def index(year: Int, quarter: Int, id: String): Action[AnyContent] = registeredAction.async { implicit request =>
    val sdilRef = request.sdilEnrolment.value
    val period = ReturnPeriod(year, quarter)
    val persistence = SaveForLaterPersistence(s"returns-$year$quarter", sdilRef, cache)
    for {
      subscription <- sdilConnector.retrieveSubscription(sdilRef).map{_.get}
      pendingReturns <- sdilConnector.returns.pending(subscription.utr)
      r   <- if (pendingReturns.contains(period))
               runInner(request)(program(period, subscription, sdilRef))(id)(persistence.dataGet,persistence.dataPut, JourneyConfig(SingleStep))
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
    val t = r.packLarge |+| r.importLarge |+| r.ownBrand
    (t._1 * costLower |+| t._2 * costHigher) * 4
  }

}
