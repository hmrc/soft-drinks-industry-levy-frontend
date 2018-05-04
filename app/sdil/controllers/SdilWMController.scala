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

import enumeratum._
import ltbs.play._
import ltbs.play.scaffold.HtmlShow
import ltbs.play.scaffold.webmonad._
import play.api.data._
import play.api.data.validation._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.twirl.api.Html
import scala.collection.mutable.{Map => MMap}
import scala.concurrent._
import scala.util.Try
import play.api.data.Forms._
import play.api.mvc.{Action, AnyContent, Request, Result}
import sdil.config.AppConfig
import sdil.models._
import sdil.models.backend._
import uk.gov.hmrc.http.{ HeaderCarrier, HttpReads }
import uk.gov.hmrc.play.HeaderCarrierConverter
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import views.html.gdspages
import cats.implicits._
import HtmlShow.ops._
import java.time.LocalDate

trait JunkPersistence {

  implicit def ec: ExecutionContext

  // crappy persistence, but good for development
  private val data = MMap.empty[String,Map[String,JsValue]]

  protected def dataGet(session: String): Future[Map[String, JsValue]] =
    data.getOrElse(session, Map.empty[String,JsValue]).pure[Future]

  protected def dataPut(session: String, dataIn: Map[String, JsValue]): Unit =
    data(session) = dataIn
}

trait SdilWMController extends WebMonadController
    with JunkPersistence
    with FrontendController
{

  implicit def config: AppConfig

  protected def askEnum[E <: EnumEntry](
    id: String,
    e: Enum[E],
    default: Option[E] = None
  ): WebMonad[E] = {
    val possValues: List[String] = e.values.toList.map{_.toString}
    formPage(id)(
      nonEmptyText.verifying(possValues.contains(_)),
      default.map{_.toString}
    ) { (path, b, r) =>
      implicit val request: Request[AnyContent] = r
      gdspages.radiolist(id, b, possValues, path)
    }.imap(e.withName)(_.toString)
  }

  protected def askBool(
    id: String,
    default: Option[Boolean] = None
  ): WebMonad[Boolean] = {
    val mapping: Mapping[Boolean] = optional(boolean)
      .verifying("error.required", _.isDefined)
      .transform(_.getOrElse(false),{x: Boolean => x.some})

    formPage(id)(mapping, default) { (path, b, r) =>
      implicit val request: Request[AnyContent] = r
      gdspages.boolean(id, b, path)
    }
  }

  protected def tellTable(
    id: String,
    headings: List[Html],
    rows: List[List[Html]]
  ): WebMonad[Unit] = {

    // Because I decided earlier on to make everything based off of JSON
    // I have to write silly things like this. TODO
    implicit val formatUnit: Format[Unit] = new Format[Unit] {
      def writes(u: Unit) = JsNull
      def reads(v: JsValue) = JsSuccess(())
    }

    val unitMapping: Mapping[Unit] = text.transform(_ => (), _ => "")
    formPage(id)(unitMapping, none[Unit]){  (path, form, r) =>
      implicit val request: Request[AnyContent] = r

      gdspages.tellTable(id, form, path, headings, rows)
    }
  }

  protected def tell[A](
    id: String,
    a: A
  )(implicit show: HtmlShow[A]): WebMonad[Unit] = {

    // Because I decided earlier on to make everything based off of JSON
    // I have to write silly things like this. TODO
    implicit val formatUnit: Format[Unit] = new Format[Unit] {
      def writes(u: Unit) = JsNull
      def reads(v: JsValue) = JsSuccess(())
    }

    val unitMapping: Mapping[Unit] = text.transform(_ => (), _ => "")

    formPage(id)(unitMapping, none[Unit]){  (path, form, r) =>
      implicit val request: Request[AnyContent] = r

      gdspages.tell(id, form, path, a.showHtml)
    }    
  }

  protected def tellTables(
    id: String,
    tables: List[(String,Table[Html,Html])]
  ): WebMonad[Unit] = {

    // Because I decided earlier on to make everything based off of JSON
    // I have to write silly things like this. TODO
    implicit val formatUnit: Format[Unit] = new Format[Unit] {
      def writes(u: Unit) = JsNull
      def reads(v: JsValue) = JsSuccess(())
    }

    val unitMapping: Mapping[Unit] = text.transform(_ => (), _ => "")
    formPage(id)(unitMapping, none[Unit]){  (path, form, r) =>
      implicit val request: Request[AnyContent] = r

      gdspages.tellTables(id, form, path, tables)
    }
  }

  protected def askString(
    id: String,
    default: Option[String] = None,
    constraints: List[(String, String => Boolean)] = Nil
  ): WebMonad[String] =
    formPage(id)(nonEmptyText.verifying(constraintMap(constraints) :_*), default) { (path, b, r) =>
    implicit val request: Request[AnyContent] = r
    gdspages.string(id, b, path)
  }

  protected def askBigText(
    id: String,
    default: Option[String] = None,
    constraints: List[(String, String => Boolean)] = Nil
  ): WebMonad[String] =
    formPage(id)(nonEmptyText.verifying(constraintMap(constraints) :_*), default) { (path, b, r) =>
      implicit val request: Request[AnyContent] = r
      gdspages.bigtext(id, b, path)
    }

  protected def askDate(
    id: String,
    default: Option[LocalDate] = None,
    constraints: List[(String, LocalDate => Boolean)] = Nil
  ): WebMonad[LocalDate] = {

    val dateMapping: Mapping[LocalDate] = tuple(
      "day" -> number(1,31),
      "month" -> number(1,12),
      "year" -> number
    ).verifying(
      "error.date.invalid", _ match {
        case (d, m, y) => Try(LocalDate.of(y, m, d)).isSuccess
      })
      .transform(
        {case (d, m, y) => LocalDate.of(y, m, d)},
        d => (d.getDayOfMonth, d.getMonthValue, d.getYear)
      )

    val constraintsP = constraintMap(constraints)

    formPage(id)(dateMapping.verifying(constraintsP :_*), default) { (path, b, r) =>
      implicit val request: Request[AnyContent] = r
      gdspages.date(id, b, path)
    }
  }

  protected def askEnumSet[E <: EnumEntry](
    id: String,
    e: Enum[E],
    default: Option[Set[E]] = None
  ): WebMonad[Set[E]] = {
    val possValues: Set[String] = e.values.toList.map{_.toString}.toSet
    formPage(id)(
      list(nonEmptyText).verifying(_.toSet subsetOf possValues),
      default.map{_.map{_.toString}.toList}
    ) { (path, b, r) =>
      implicit val request: Request[AnyContent] = r
      gdspages.checkboxes(id, b, possValues.toList, path)
    }.imap(_.map{e.withName}.toSet)(_.map(_.toString).toList)
  }

  protected def journeyEnd(id: String): WebMonad[Result] =
    webMonad{ (rid, request, path, db) =>
      implicit val r = request

      Future.successful {
        (id.some, path, db, Ok(gdspages.journeyEnd(id, path)).asRight[Result])
      }
    }

  protected def askSite(
    id: String,
    default: Option[Site] = None,
    constraints: List[(String, Site => Boolean)] = Nil
  ): WebMonad[Site] = {

    val siteMapping = mapping(
      "address" -> ukAddressMapping
    ){a => Site.apply(a, none, none, none)}(Site.unapply(_).map{
      case (address, refOpt, _, _) => address })
    .verifying(constraintMap(constraints) :_*)

    formPage(id)(siteMapping, default) { (path, b, r) =>
      implicit val request: Request[AnyContent] = r
      gdspages.site(id, b, path)
    }
  }

  private val addressMapping: play.api.data.Mapping[Address] = mapping(
    "line1" -> nonEmptyText,
    "line2" -> text,
    "line3" -> text,
    "line4" -> text,
    "postcode" -> nonEmptyText
  )(Address.apply)(Address.unapply)

  private val ukAddressMapping: Mapping[UkAddress] =
    addressMapping.transform(UkAddress.fromAddress, Address.fromUkAddress)

  protected def askAddress(
    id: String,
    default: Option[Address] = None,
    constraints: List[(String, Address => Boolean)] = Nil
  ): WebMonad[Address] = 
    formPage(id)(addressMapping.verifying(constraintMap(constraints):_*), default) { (path, b, r) =>
      implicit val request: Request[AnyContent] = r
      gdspages.address(id, b, path)
    }

  implicit val longTupleFormatter: Format[(Long, Long)] = (
    (JsPath \ "lower").format[Long] and
    (JsPath \ "higher").format[Long]
  )((a: Long, b: Long) => (a,b), unlift({x: (Long,Long) => Tuple2.unapply(x)}))

  protected def askLitreage(
    id: String,
    default: Option[(Long,Long)] = None,
    allowZero: Boolean = false,
    constraints: List[(String, ((Long,Long)) => Boolean)] = Nil
  ): WebMonad[(Long,Long)] = {

    val litreage: Mapping[Long] = nonEmptyText
      .transform[String](_.replaceAll(",", ""), _.toString)
      .verifying("error.litreage.numeric", l => Try(BigDecimal.apply(l)).isSuccess)
      .transform[BigDecimal](BigDecimal.apply, _.toString)
      .verifying("error.litreage.numeric", _.isWhole)
      .verifying("error.litreage.max", _ <= 9999999999999L)
      .verifying("error.litreage.min", _ >= 0)
      .transform[Long](_.toLong, BigDecimal.apply)

    val constraintsP:List[Constraint[(Long,Long)]] = constraintMap(
      constraints ++ List(("error.litreage.zero", {a: (Long,Long) => !a.isEmpty || allowZero}))
    )

    formPage(id)(
      tuple("lower" -> litreage, "higher" -> litreage)
        .verifying(constraintsP:_*),
      default
    ){ (path,b,r) =>
      implicit val request: Request[AnyContent] = r
      gdspages.literage(id, b, path)
    }
  }

  protected def manyT[A](
    id: String,
    wm: String => WebMonad[A],
    min: Int = 0,
    max: Int = 100,
    default: List[A] = List.empty[A]
  )(implicit hs: HtmlShow[A], format: Format[A]): WebMonad[List[A]] = {
    def outf(x: String): Control = x match {
      case "Add" => Add
      case "Done" => Done
      case x if x.startsWith("Delete") => Delete(x.split("\\.").last.toInt)
    }
    def inf(x: Control): String = x.toString

    many[A](id, min, max, default){ case (iid, minA, maxA, items) =>

      val mapping = nonEmptyText
        .verifying("error.items.toFew", a => a != "Done" || items.size >= min)
        .verifying("error.items.toMany", a => a != "Add" || items.size < max)

      formPage(id)(mapping) { (path, b, r) =>
        implicit val request: Request[AnyContent] = r
        gdspages.many(id, b, items.map{_.showHtml}, path)
      }.imap(outf)(inf)
    }(wm)
  }

  // private def httpGetBase[A](cacheId: Option[String], uri: String)
  //   (implicit reads: HttpReads[A], formats: Format[A]): WebMonad[A] =
  //   webMonad{ (id, request, path, db) =>
  //     implicit val hc: HeaderCarrier =
  //       HeaderCarrierConverter.fromHeadersAndSession(
  //         request.headers,
  //         Some(request.session)
  //       )

  //     val cached = for {
  //       cid <- cacheId
  //       record <- db.get(cid)
  //     } yield (none[String], path, db, record.as[A].asRight[Result])

  //     cached.map{_.pure[Future]}.getOrElse {
  //       val record = httpClient.GET[A](uri)
        
  //       record.map{r =>
  //         val newDb = cacheId.map {
  //           cid => db + (cid -> Json.toJson(r))
  //         } getOrElse db
  //         (none[String], path, newDb, r.asRight[Result])
  //       }
  //     }
      
  //   }

  // def httpGet[A](cacheId: String, uri: String)
  //   (implicit reads: HttpReads[A], formats: Format[A]): WebMonad[A] =
  //   httpGetBase(cacheId.some, uri)
  // def httpGetNoCache[A](uri: String)
  //   (implicit reads: HttpReads[A], formats: Format[A]): WebMonad[A] =
  //   httpGetBase(none[String], uri)


  // TODO: Prevent this ugliness by making the mapping the centre of the
  // webmonad form construction
  private def constraintMap[T](
    constraints: List[(String, T => Boolean)]
  ): List[Constraint[T]] =
    constraints.map{ case (error, pred) =>
      Constraint { t: T =>
        if (pred(t)) Valid else Invalid(Seq(ValidationError(error)))
      }
    }


}
