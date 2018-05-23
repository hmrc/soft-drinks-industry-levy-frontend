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
import cats.{Eq, Monoid}
import enumeratum._
import ltbs.play.scaffold.HtmlShow.ops._
import ltbs.play.scaffold._
import ltbs.play.scaffold.webmonad._
import play.api.data.Forms._
import play.api.data._
import play.api.data.format.Formatter
import play.api.data.validation._
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.mvc.{AnyContent, Request, Result}
import sdil.config.AppConfig
import sdil.models._
import sdil.models.backend._
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import views.html.uniform

import scala.concurrent._
import scala.util.Try
import scala.collection.mutable.{Map => MMap}
import scala.concurrent._
import scala.util.Try

trait SdilWMController extends WebMonadController
    with FrontendController with GdsComponents
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
      uniform.radiolist(id, b, possValues, path)
    }.imap(e.withName)(_.toString)
  }


  protected def askEnumSet[E <: EnumEntry](
    id: String,
    e: Enum[E],
    minSize: Int = 0,
    default: Option[Set[E]] = None
  ): WebMonad[Set[E]] = {
    val possValues: Set[String] = e.values.toList.map{_.toString}.toSet
    formPage(id)(
      list(nonEmptyText)
        .verifying(_.toSet subsetOf possValues)
        .verifying("error.radio-form.choose-one-option", _.size >= minSize),
      default.map{_.map{_.toString}.toList}
    ) { (path, form, r) =>
      implicit val request: Request[AnyContent] = r
      val innerHtml = uniform.fragments.checkboxes(id, form, possValues.toList)
      uniform.ask(id, form, innerHtml, path)
    }.imap(_.map{e.withName}.toSet)(_.map(_.toString).toList)
  }

  val litreage: Mapping[Long] = text
    .verifying("error.litreage.required", _.nonEmpty)
    .transform[String](_.replaceAll(",", ""), _.toString)
    .verifying("error.litreage.numeric", l => Try(BigDecimal.apply(l)).isSuccess)
    .transform[BigDecimal](BigDecimal.apply, _.toString)
    .verifying("error.litreage.numeric", _.isWhole)
    .verifying("error.litreage.max", _ <= 9999999999999L)
    .verifying("error.litreage.min", _ >= 0)
    .transform[Long](_.toLong, BigDecimal.apply)

  val litreagePair: Mapping[(Long,Long)] =
    tuple("lower" -> litreage, "higher" -> litreage)

  implicit class RichMapping[A](mapping: Mapping[A]) {
    def nonEmpty(errorMsg: String)(implicit m: Monoid[A], eq: Eq[A]): Mapping[A] =
      mapping.verifying(errorMsg, {!_.isEmpty})

    def nonEmpty(implicit m: Monoid[A], eq: Eq[A]): Mapping[A] = nonEmpty("error.empty")
  }

  def ask[T](mapping: Mapping[T], key: String, default: Option[T] = None)(implicit htmlForm: FormHtml[T], fmt: Format[T]): WebMonad[T] =
    formPage(key)(mapping, default) { (path, form, r) =>
      implicit val request: Request[AnyContent] = r
      uniform.ask(key, form, htmlForm.asHtmlForm(key, form), path)
    }

  def ask[T](mapping: Mapping[T], key: String)(implicit htmlForm: FormHtml[T], fmt: Format[T]): WebMonad[T] =
    ask(mapping, key, None)

  def ask[T](mapping: Mapping[T], key: String, default: T)(implicit htmlForm: FormHtml[T], fmt: Format[T]): WebMonad[T] =
    ask(mapping, key, default.some)

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

    val unitMapping: Mapping[Unit] = Forms.of[Unit](new Formatter[Unit] {
      override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Unit] = Right(())

      override def unbind(key: String, value: Unit): Map[String, String] = Map.empty
    })

    formPage(id)(unitMapping, none[Unit]){  (path, form, r) =>
      implicit val request: Request[AnyContent] = r

      uniform.tell(id, form, path, a.showHtml)
    }
  }

  protected def askBigText(
    id: String,
    default: Option[String] = None,
    constraints: List[(String, String => Boolean)] = Nil,
    errorOnEmpty: String = "error.required"
  ): WebMonad[String] = {
    val mapping = text.verifying(errorOnEmpty, _.nonEmpty).verifying(constraintMap(constraints) :_*)
    formPage(id)(mapping, default) { (path, b, r) =>
      implicit val request: Request[AnyContent] = r
      uniform.bigtext(id, b, path)
    }
  }

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

  protected def journeyEnd(id: String): WebMonad[Result] =
    webMonad{ (rid, request, path, db) =>
      implicit val r = request

      Future.successful {
        (id.some, path, db, Ok(uniform.journeyEnd(id, path)).asRight[Result])
      }
    }


  // TODO these were lifted from FormHelpers which has differing implementations of other mappings - resolve
  lazy val tradingNameMapping: Mapping[String] = {
    text.transform[String](_.trim, s => s).verifying(optionalTradingNameConstraint)
  }

  private def optionalTradingNameConstraint: Constraint[String] = Constraint {
    case b if b.length > 160 => Invalid(s"error.tradingName.over")
    case a if !a.matches("""^[a-zA-Z0-9 '.&\\/]{1,160}$""") => Invalid(s"error.tradingName.invalid")
    case _ => Valid
  }

  lazy val warehouseSiteMapping: Mapping[Site] = mapping(
    "address" -> ukAddressMapping,
    "tradingName" -> optional(tradingNameMapping)
  ){(a,b) => Site.apply(a, none, b, none)}(Site.unapply(_).map{ case (address, refOpt, tradingName, _) => (address, tradingName) } )

  lazy val packagingSiteMapping: Mapping[Site] = mapping(
    "address" -> ukAddressMapping
  ){a => Site.apply(a, none, none, none)}(Site.unapply(_).map{ case (address, refOpt, _, _) =>
    address } )

  protected val addressMapping: play.api.data.Mapping[Address] = mapping(
    "line1" -> nonEmptyText,
    "line2" -> text,
    "line3" -> text,
    "line4" -> text,
    "postcode" -> nonEmptyText
  )(Address.apply)(Address.unapply)

  private val ukAddressMapping: Mapping[UkAddress] =
    addressMapping.transform(UkAddress.fromAddress, Address.fromUkAddress)

  val contactDetailsMapping: Mapping[ContactDetails] = mapping(
    "fullName" -> nonEmptyText,
    "position" -> nonEmptyText,
    "phoneNumber" -> nonEmptyText,
    "email" -> nonEmptyText
  )(ContactDetails.apply)(ContactDetails.unapply)

  implicit val longTupleFormatter: Format[(Long, Long)] = (
    (JsPath \ "lower").format[Long] and
    (JsPath \ "higher").format[Long]
  )((a: Long, b: Long) => (a,b), unlift({x: (Long,Long) => Tuple2.unapply(x)}))

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

    def confirmation(q: A): WebMonad[Boolean] =
      tell(s"${id}_deleteConfirmation", q).map{_ => true}

    many[A](id, min, max, default, confirmation){ case (iid, minA, maxA, items) =>

      val mapping = nonEmptyText
        .verifying("error.items.toFew", a => a != "Done" || items.size >= min)
        .verifying("error.items.toMany", a => a != "Add" || items.size < max)

      formPage(id)(mapping) { (path, b, r) =>
        implicit val request: Request[AnyContent] = r
        uniform.many(id, b, items.map{_.showHtml}, path)
      }.imap(outf)(inf)
    }(wm)
  }

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
