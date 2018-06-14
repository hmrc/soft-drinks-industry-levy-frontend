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
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import views.html.uniform
import ltbs.play.scaffold.GdsComponents._
import ltbs.play.scaffold.SdilComponents.{litreageForm => _, _}
import scala.concurrent.ExecutionContext

class ReturnsController (
  val messagesApi: MessagesApi,
  sdilConnector: SoftDrinksIndustryLevyConnector,
  registeredAction: RegisteredAction,
  keystore: SessionCache
)(implicit
  val config: AppConfig,
  val ec: ExecutionContext
) extends SdilWMController with FrontendController {

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
      Html(s"${producer.sdilRef} ${producer.litreage}")
    }

  implicit val smallProducer: Mapping[SmallProducer] = mapping(
    "sdilRef" -> nonEmptyText
      .verifying("error.sdilref.invalid", _.matches("^X[A-Z]SDIL000[0-9]{6}$")),
    "lower"   -> litreage,
    "higher"  -> litreage
  ){
    (ref,l,h) => SmallProducer(ref, (l,h))
  }{
    case SmallProducer(ref, (l,h)) => (ref,l,h).some
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

  private val askReturn: WebMonad[SdilReturn] = (
    askEmptyOption(litreagePair.nonEmpty, "own-brands"),
    askEmptyOption(litreagePair.nonEmpty, "copack-large"),
    askList(smallProducer, "copack-small", min = 1) emptyUnless ask(bool, "copack-small-yn"),
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

  private val program: WebMonad[Result] = for {
    sdilReturn     <- askReturn
    broughtForward <- BigDecimal("0").pure[WebMonad]
    _              <- checkYourAnswers("returns-cya", sdilReturn, broughtForward)
    end            <- confirmationPage("returnsDone")
  } yield end

  def index(id: String): Action[AnyContent] = Action.async { implicit request =>
    val persistence = SessionCachePersistence("returns", keystore)
    runInner(request)(program)(id)(persistence.dataGet,persistence.dataPut)
  }
}
