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
import play.api.i18n.MessagesApi
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.mvc.{Action, AnyContent, Request, Result}

import scala.concurrent.{ExecutionContext, Future}
import sdil.config.AppConfig
import sdil.connectors.SoftDrinksIndustryLevyConnector
import uk.gov.hmrc.http.cache.client.SessionCache
import webmonad._

import scala.collection.mutable.{Map => MMap}
import play.api.data.Forms._
import views.html.gdspages
import enumeratum._
import sdil.models.{Address, Litreage}
import sdil.models.variations.VariationData

class ReturnsController(val messagesApi: MessagesApi,
                        sdilConnector: SoftDrinksIndustryLevyConnector,
                        keystore: SessionCache)
                       (implicit config: AppConfig, val ec: ExecutionContext)
    extends WebMonadController {

  private val data = MMap.empty[String,Map[String,JsValue]]

  private def dataGet(session: String): Future[Map[String, JsValue]] = {
    val ret = data.getOrElse(session, Map.empty)
    println(s"Fetch $session gives $ret")
    ret
  }

  protected def askEnum[E <: EnumEntry](id: String, e: Enum[E]): WebMonad[E] = {
    val possValues: List[String] = e.values.toList.map{_.toString}
    formPage(id)(nonEmptyText.verifying(possValues.contains(_))) { (a, b, r) =>
      implicit val request: Request[AnyContent] = r
      gdspages.radiolist(id, b, possValues)
    }.imap(e.withName)(_.toString)
  }

  protected def askBool(id: String): WebMonad[Boolean] =
    formPage(id)(boolean) { (a, b, r) =>
    implicit val request: Request[AnyContent] = r
    gdspages.boolean(id, b)
  }


  protected def askEnumSet[E <: EnumEntry](id: String, e: Enum[E]): WebMonad[Set[E]] = {
    val possValues: Set[String] = e.values.toList.map{_.toString}.toSet
    formPage(id)(list(nonEmptyText).verifying(_.toSet subsetOf possValues)) { (a, b, r) =>
      implicit val request: Request[AnyContent] = r
      gdspages.checkboxes(id, b, possValues.toList)
    }.imap(_.map{e.withName}.toSet)(_.map(_.toString).toList)
  }

  protected def askSite(id: String): WebMonad[Address] = {
    val siteMapping: play.api.data.Mapping[Address] = mapping(
      "line1" -> nonEmptyText,
      "line2" -> text,
      "line3" -> text,
      "line4" -> text,
      "postcode" -> nonEmptyText
    )(Address.apply)(Address.unapply)

    formPage(id)(siteMapping) { (a, b, r) =>
      implicit val request: Request[AnyContent] = r
      gdspages.site(id, b)
    }
  }

  implicit val longTupleFormatter: Format[(Long, Long)] = (
    (JsPath \ "lower").format[Long] and
    (JsPath \ "higher").format[Long]
  )((a: Long, b: Long) => (a,b), unlift({x: (Long,Long) => Tuple2.unapply(x)}))

  protected def askLitreage(id: String): WebMonad[(Long,Long)] =
    formPage(id)(tuple("lower" -> longNumber, "higher" -> longNumber)){ (a,b,r) =>
      implicit val request: Request[AnyContent] = r
      gdspages.literage(id, b)
    }

  private def dataPut(session: String, dataIn: Map[String, JsValue]): Unit = {
    data(session) = dataIn
    println(s"Store $dataIn to $session")
  }


  protected def manyT[A](id: String, wm: String => WebMonad[A], min: Int = 0, max: Int = 100)(implicit hs: HtmlShow[A], format: Format[A]): WebMonad[List[A]] = {
    import HtmlShow.ops._
    val possValues: List[String] = List("Add","Done")
    def outf(x: String): Control = x match {
      case "Add" => Add
      case "Done" => Done
    }
    def inf(x: Control): String = x.toString

    many[A](id,min,max){ case (iid, min, max, items) =>

      formPage(id)(nonEmptyText) { (a, b, r) =>
        implicit val request: Request[AnyContent] = r
        gdspages.many(id, b, items.map{_.showHtml})
      }.imap(outf)(inf)
    }(wm)
  }

  sealed trait ChangeType extends EnumEntry

  object ChangeType extends Enum[ChangeType] {
    val values = findValues

    case object Sites extends ChangeType
    case object Activity extends ChangeType
    case object Deregister extends ChangeType
  }

  private lazy val contactUpdate: WebMonad[VariationData] = {
    throw new NotImplementedError()
  }

  implicit class RichWebMonadBoolean(wmb: WebMonad[Boolean]) {
    def andIfTrue[A](next: WebMonad[A]): WebMonad[Option[A]] = for {
      opt <- wmb
      ret <- if (opt) next map {_.some} else none[A].pure[WebMonad]
    } yield ret
  }

  def when[A](b: => Boolean)(wm: WebMonad[A]): WebMonad[Option[A]] =
    if(b) wm.map{_.some} else none[A].pure[WebMonad]

  private val activityUpdate: WebMonad[VariationData] = for {
    packSites <- manyT("packSites", askSite(_))
    packLarge <- askBool("package") andIfTrue askBool("packLarge")
    packQty <- when (packLarge == Some(true)) (askLitreage("packQty"))

    thirdParties <- askBool("thirdParties")
    operateSites <- askBool("operateSites")
    imports <- askBool("importer") andIfTrue askLitreage("importQty")
  } yield VariationData(
    original = ???,
    updatedBusinessDetails = ???,
    producer = ???,
    usesCopacker = ???,
    packageOwn = packLarge,
    packageOwnVol = packQty.map{case (a,b) => Litreage(a,b)},
    copackForOthers = ???,
    copackForOthersVol = ???,
    imports = ???,
    importsVol = ???,
    updatedProductionSites = ???,
    updatedWarehouseSites = ???,
    updatedContactDetails = ???,
    previousPages = Nil
  )

  private lazy val deregisterUpdate: WebMonad[VariationData] = {
    throw new NotImplementedError()
  }

  private val getVariation: WebMonad[VariationData] = for {
    changeType <- askEnum("changeType", ChangeType)
    variation <- changeType match {
      case ChangeType.Sites => contactUpdate
      case ChangeType.Activity => activityUpdate
      case ChangeType.Deregister => deregisterUpdate
    }
  } yield {
    variation
  }

  private val program: WebMonad[Result] = for {
    changeType <- getVariation

    x <- formPage("x" )(nonEmptyText.verifying(_.contains("x"))){ (a,b,r) =>
      implicit val request: Request[AnyContent] = r
      gdspages.string(a,b)
    }
    y <- formPage("y" )(nonEmptyText){ (a,b,r) =>
      implicit val request: Request[AnyContent] = r
      gdspages.string(a,b)
    }
  } yield {
    Ok(s"$changeType $x $y")
  }

  def index(id: String): Action[AnyContent] = run(program)(id)(dataGet,dataPut)

}
