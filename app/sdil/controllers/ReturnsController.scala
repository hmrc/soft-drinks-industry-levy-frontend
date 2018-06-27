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
import ltbs.play.scaffold._
import ltbs.play.scaffold.webmonad._
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.Mapping
import play.api.i18n.{ Messages, MessagesApi }
import play.api.libs.json._
import play.api.mvc._
import play.twirl.api.Html
import sdil.actions.RegisteredAction
import sdil.config._
import sdil.connectors.SoftDrinksIndustryLevyConnector
import sdil.models._
import sdil.uniform.SessionCachePersistence
import uk.gov.hmrc.domain.Modulus23Check
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import views.html.uniform
import ltbs.play.scaffold.GdsComponents._
import ltbs.play.scaffold.SdilComponents.{litreageForm => _, _}
import scala.concurrent._
import scala.concurrent.duration._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import uk.gov.hmrc.http.HeaderCarrier

class ReturnsController (
  val messagesApi: MessagesApi,
  sdilConnector: SoftDrinksIndustryLevyConnector,
  registeredAction: RegisteredAction,
  keystore: SessionCache
)(implicit
  val config: AppConfig,
  val ec: ExecutionContext
) extends SdilWMController with FrontendController with Modulus23Check {

  implicit val litreageForm = new FormHtml[(Long,Long)] {
    import play.api.data.Forms._
    import play.api.data._
    import play.api.i18n.Messages

    def asHtmlForm(key: String, form: Form[(Long,Long)])(implicit messages: Messages): Html = {
      uniform.fragments.litreage(key, form, false)(messages)
    }
  }

  implicit val address: Format[SmallProducer] = Json.format[SmallProducer]

  implicit val smallProducerHtml: HtmlShow[SmallProducer] =
    HtmlShow.instance { producer =>
      Html(s"<h3>${producer.alias} (${producer.sdilRef})</h3> <br />" ++
        f"Lower band: ${producer.litreage._1}%,d litres <br />" ++
        f"Upper band: ${producer.litreage._2}%,d litres")
    }

  // TODO: At present this uses an Await.result to check the small producer status, thus
  // blocking a thread. At a later date uniform should be updated to include the capability
  // for a subsequent stage to invalidate a prior one.
  implicit def smallProducer(implicit hc: HeaderCarrier): Mapping[SmallProducer] = mapping(
    "alias" -> nonEmptyText,
    "sdilRef" -> nonEmptyText
      .verifying("error.sdilref.invalid", _.matches("^X[A-Z]SDIL000[0-9]{6}$"))
      .verifying("error.sdilref.invalid", x => isCheckCorrect(x,1))
      .verifying("error.sdilref.notsmall", ref => Await.result(isSmallProducer(ref), 20.seconds)),
    "lower"   -> litreage,
    "higher"  -> litreage
  ){
    (alias, ref,l,h) => SmallProducer(alias, ref, (l,h))
  }{
    case SmallProducer(alias, ref, (l,h)) => (alias, ref,l,h).some
  }

  def checkYourAnswers(key: String, sdilReturn: SdilReturn, broughtForward: BigDecimal): WebMonad[Unit] = {

    val data = List(
      ("own-brands", sdilReturn.ownBrand, 1),
      ("copack-large", sdilReturn.packLarge, 1),
      ("copack-small", sdilReturn.packSmall.map{_.litreage}.combineAll, 0),
      ("imports-large", sdilReturn.importLarge, 1),
      ("imports-small", sdilReturn.importSmall, 1),
      ("export", sdilReturn.export, -1),
      ("waste", sdilReturn.wastage, -1)
    )

    val costLower = BigDecimal("0.18")
    val costHigher = BigDecimal("0.24")
    val subtotal = data.map{case (_, (l,h), m) => costLower * l * m + costHigher * h * m}.sum

    val inner = uniform.fragments.returnsCYA(
      key = key,
      lineItems = data,
      costLower,
      costHigher,
      subtotal = subtotal,
      broughtForward = broughtForward,
      total = subtotal + broughtForward)
    tell(key, inner)
  }

  private def askReturn(implicit hc: HeaderCarrier): WebMonad[SdilReturn] = (
    askEmptyOption(litreagePair.nonEmpty, "own-brands"),
    askEmptyOption(litreagePair.nonEmpty, "copack-large"),
    //askList(smallProducer, "copack-small", min = 1) emptyUnless ask(bool, "copack-small-yn"),
    manyT("copack-small", {ask(smallProducer, _)}, min = 1) emptyUnless ask(bool, "copack-small-yn"),
    askEmptyOption(litreagePair.nonEmpty, "imports-large"),
    askEmptyOption(litreagePair.nonEmpty, "imports-small"),
    askEmptyOption(litreagePair.nonEmpty, "export"),
    askEmptyOption(litreagePair.nonEmpty, "waste")
  ).mapN(SdilReturn.apply)

  private def confirmationPage(key: String)(implicit messages: Messages): WebMonad[Result] = {
    val now = java.time.LocalDate.now

    val returnDate = messages("returnsDone.returnsDoneMessage", "April", "June", "2018", "ABC Drinks", "12:12", "12th June")
    val whatHappensNext = uniform.fragments.returnsPaymentsBlurb(now)(messages).some
    journeyEnd(key, now, Html(returnDate).some, whatHappensNext)
  }

  private def program(period: ReturnPeriod, utr: String)(implicit hc: HeaderCarrier): WebMonad[Result] = for {
    sdilReturn     <- askReturn
    broughtForward <- BigDecimal("0").pure[WebMonad]
    _              <- checkYourAnswers("returns-cya", sdilReturn, broughtForward)
    _              <- cachedFuture(s"return-${period.count}")(
                        sdilConnector.returns(utr, period) = sdilReturn)
    end            <- clear >> confirmationPage("returnsDone")

  } yield end

  def index(year: Int, quarter: Int, id: String): Action[AnyContent] = registeredAction.async { implicit request =>
    if (!config.returnsEnabled)
      throw new NotImplementedError("Returns are not enabled")
    val sdilRef = request.sdilEnrolment.value
    //val utr = request.utr.get

    val period = ReturnPeriod(year, quarter)
    val persistence = SessionCachePersistence(s"returns-${period.year}-${period.quarter}", keystore)

    for {
      utr <- sdilConnector.retrieveSubscription(sdilRef).map{_.get.utr}
      pendingReturns <- sdilConnector.returns.pending(utr)
      r   <- if (pendingReturns.contains(period))
               runInner(request)(program(period, utr))(id)(persistence.dataGet,persistence.dataPut)
             else 
               Ok("Unexpected").pure[Future]
    } yield r
  }

  def isSmallProducer(sdilRef: String)(implicit hc: HeaderCarrier): Future[Boolean] = 
    sdilConnector.retrieveSubscription(sdilRef).flatMap {
      case Some(x) => x.activity.smallProducer
      case None    => false
    }

}
