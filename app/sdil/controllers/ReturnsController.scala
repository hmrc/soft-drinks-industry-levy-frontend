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
import java.time.LocalDate
import ltbs.play.scaffold._
import play.api.i18n.MessagesApi
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.mvc._
import play.twirl.api.Html
import scala.concurrent.{ ExecutionContext, Future }
import sdil.actions.RegisteredAction
import sdil.config._
import sdil.connectors.SoftDrinksIndustryLevyConnector
import sdil.models.backend.{ Contact, Site, UkAddress }
import sdil.models.retrieved.{ RetrievedActivity, RetrievedSubscription }
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import webmonad._
import scala.collection.mutable.{Map => MMap}
import play.api.data.Forms._
import views.html.gdspages
import enumeratum._
import sdil.models._
import play.api.data.Mapping
import scala.util.Try

class ReturnsController (
  val messagesApi: MessagesApi,
  sdilConnector: SoftDrinksIndustryLevyConnector,
  registeredAction: RegisteredAction,
  keystore: SessionCache
)(implicit
  val config: AppConfig,
  val ec: ExecutionContext
) extends SdilWMController with FrontendController {

  implicit val address: Format[SmallProducer] = Json.format[SmallProducer]

  implicit val smallProducerHtml: HtmlShow[SmallProducer] =
    HtmlShow.instance { producer =>
      Html(s"${producer.sdilRef} ${producer.litreage}")
    }

  protected def askSmallProducer(
    id: String,
    default: Option[SmallProducer] = None
  ): WebMonad[SmallProducer] = {

    val litreage: Mapping[Long] = nonEmptyText
      .transform[String](_.replaceAll(",", ""), _.toString)
      .verifying("error.litreage.numeric", l => Try(BigDecimal.apply(l)).isSuccess)
      .transform[BigDecimal](BigDecimal.apply, _.toString)
      .verifying("error.litreage.numeric", _.isWhole)
      .verifying("error.litreage.max", _ <= 9999999999999L)
      .verifying("error.litreage.min", _ >= 0)
      .transform[Long](_.toLong, BigDecimal.apply)

    formPage(id)(
      mapping(
        "sdilRef" -> nonEmptyText.verifying("error.sdilref.invalid", _.matches("^X[A-Z]SDIL000[0-9]{6}$")),
        "lower" -> litreage,
        "higher" -> litreage){
        (ref,l,h) => SmallProducer(ref, (l,h))
      }{
        case SmallProducer(ref, (l,h)) => (ref,l,h).some
      },
      default
    ){ (path,b,r) =>
      implicit val request: Request[AnyContent] = r
      gdspages.smallProducer(id, b, path)
    }
  }

  def askLitreageOpt(key: String): WebMonad[(Long, Long)] =
    askLitreage(key + "Qty") emptyUnless askBool(key + "YesNo")

  def checkYourAnswers(key: String)(sdilReturn: SdilReturn): WebMonad[Unit] = {

    //TODO: The rates should be read in from somewhere not just dumped in
    // a function
    def taxRow(in: (Long, Long), rate: Int = 1): (Long, Long, BigDecimal, BigDecimal) = {
      val tax = in.combineN(rate)
      (in._1, in._2, BigDecimal("0.18") * tax._1, BigDecimal("0.24") * tax._2)
    }

    implicit def stringToHtml(i: String): Html = Html(i)

    val data = List(
      "packageSmall" -> taxRow(sdilReturn.packageSmall.map{_.litreage}.combineAll, 0),
      "packageLarge" -> taxRow(sdilReturn.packageLarge),
      "importSmall"  -> taxRow(sdilReturn.importSmall),
      "importLarge"  -> taxRow(sdilReturn.importLarge),
      "export"       -> taxRow(sdilReturn.export, -1),
      "wastage"      -> taxRow(sdilReturn.wastage, -1)
    )

    val totals = ("total" -> data.map{_._2}.combineAll)

    tellTable(
      key,
      headings = List("", "litres.lower", "litres.higher", "tax.lower", "tax.higher", "subtotal"),
      rows = {data :+ totals}.map{ case (key, (lower, higher, taxLow, taxHigh)) =>
        List(key, f"$lower%,d", f"$higher%,d", f"£$taxLow%,.2f", f"£$taxHigh%,.2f", f"£${taxLow+taxHigh}%,.2f")
          .map(stringToHtml)
      }
    )
  }

  private val askReturn: WebMonad[SdilReturn] = (
    manyT("packageSmall", askSmallProducer(_), min = 1) emptyUnless askBool("packageSmallYN"),
    askLitreageOpt("packageLarge"),
    askLitreageOpt("importSmall"),
    askLitreageOpt("importLarge"),
    askLitreageOpt("export"),
    askLitreageOpt("wastage")
  ).mapN(SdilReturn.apply)

  private val program: WebMonad[Result] = for {
    sdilReturn   <- askReturn
    _            <- checkYourAnswers("check")(sdilReturn)
    exit         <- journeyEnd("returnsDone")
  } yield {
    exit
  }

  def index(id: String): Action[AnyContent] = Action.async { implicit request =>
    runInner(request)(program)(id)(dataGet,dataPut)
  }
}
