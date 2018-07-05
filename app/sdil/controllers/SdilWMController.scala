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
import play.api.i18n.Messages
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.mvc.{AnyContent, Request, Result}
import play.twirl.api.Html
import sdil.config.AppConfig
import sdil.forms.FormHelpers
import sdil.models._
import sdil.models.backend._
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import views.html.uniform
import ltbs.play.scaffold.SdilComponents._
import scala.concurrent._
import scala.util.Try
import scala.collection.mutable.{Map => MMap}
import scala.concurrent._
import scala.util.Try
import ltbs.play.scaffold.GdsComponents._
import ltbs.play.scaffold.SdilComponents.packagingSiteForm

trait SdilWMController extends WebMonadController
    with FrontendController
{

  implicit def config: AppConfig

  protected def askEnum[E <: EnumEntry](
    id: String,
    e: Enum[E],
    default: Option[E] = None
  ): WebMonad[E] = askOneOf(id, e.values.toList, default)

  protected def askOneOf[A](
    id: String,
    possValues: List[A],
    default: Option[A] = None,
    sort: (((String, String),(String,String)) => Boolean) = _._2 < _._2
  ): WebMonad[A] = {

    val valueMap: Map[String,A] =
      possValues.map{a => (a.toString, a)}.toMap
    formPage(id)(
      oneOf(possValues.map{_.toString}, "error.radio-form.choose-option"),
      default.map{_.toString}
    ) { (path, b, r) =>
      implicit val request: Request[AnyContent] = r
      val inner = uniform.fragments.radiolist(id, b, valueMap.keys.toList, sort)
      uniform.ask(id, b, inner, path)
    }.imap(valueMap(_))(_.toString)
  }

  protected def askSet[E](
                                  id: String,
                                  possibleValues: Set[E],
                                  minSize: Int = 0,
                                  default: Option[Set[E]] = None
                                          ): WebMonad[Set[E]] = {
    val valueMap: Map[String,E] =
      possibleValues.map{a => (a.toString, a)}.toMap
    formPage(id)(
      list(nonEmptyText)
        .verifying(_.toSet subsetOf possibleValues.map(_.toString))
        .verifying("error.radio-form.choose-one-option", _.size >= minSize),
      default.map{_.map{_.toString}.toList}
    ) { (path, form, r) =>
      implicit val request: Request[AnyContent] = r
      val innerHtml = uniform.fragments.checkboxes(id, form, valueMap.keys.toList)
      uniform.ask(id, form, innerHtml, path)
    }.imap(_.map {valueMap(_)}.toSet)(_.map(_.toString).toList)
  }

  implicit class RichMapping[A](mapping: Mapping[A]) {
    def nonEmpty(errorMsg: String)(implicit m: Monoid[A], eq: Eq[A]): Mapping[A] =
      mapping.verifying(errorMsg, {!_.isEmpty})

    def nonEmpty(implicit m: Monoid[A], eq: Eq[A]): Mapping[A] = nonEmpty("error.empty")
  }


  implicit def optFormatter[A](implicit innerFormatter: Format[A]): Format[Option[A]] =
    new Format[Option[A]] {
      def reads(json: JsValue): JsResult[Option[A]] = json match {
        case JsNull => JsSuccess(none[A])
        case a      => innerFormatter.reads(a).map{_.some}
      }
      def writes(o: Option[A]): JsValue =
        o.map{innerFormatter.writes}.getOrElse(JsNull)
    }


  def askOption[T](innerMapping: Mapping[T], key: String, default: Option[Option[T]] = None)(implicit htmlForm: FormHtml[T], fmt: Format[T]): WebMonad[Option[T]] = {
    import uk.gov.voa.play.form.ConditionalMappings.mandatoryIfTrue

    val outerMapping: Mapping[Option[T]] = mapping(
      "outer" -> bool,
      "inner" -> mandatoryIfTrue(s"${key}.outer", innerMapping)
    ){(_,_) match { case (outer, inner) => inner }
    }( a => (a.isDefined, a).some )

    formPage(key)(outerMapping, default) { (path, form, r) =>
      implicit val request: Request[AnyContent] = r

      val innerForm: Form[T] = Form(single { key -> single { "inner" -> innerMapping }})
      val innerFormBound = if (form.data.get(s"$key.outer") != Some("true")) innerForm else innerForm.bind(form.data)

      val innerHead = views.html.uniform.fragments.innerhead(key)
      val innerHtml = htmlForm.asHtmlForm(key + ".inner", innerFormBound)

      val outerHtml = {
        import ltbs.play._
        views.html.softdrinksindustrylevy.helpers.inlineRadioButtonWithConditionalContent(
          form(s"${key}.outer"),
          Seq(
            "true" -> ("Yes", Some("hiddenTarget")),
            "false" -> ("No", None)
          ),
          Some(innerHead |+| innerHtml),
          '_labelClass -> "block-label",
          '_labelAfter -> true,
          '_groupClass -> "form-field-group inline",
          '_dataTargetTrue -> s"anything",
          '_legendClass -> "visuallyhidden"
        )
      }

      uniform.ask(key, form, outerHtml, path)
    }
  }

  def askEmptyOption[T](
    innerMapping: Mapping[T],
    key: String,
    default: Option[T] = None
  )(implicit htmlForm: FormHtml[T],
    fmt: Format[T],
    mon: Monoid[T]
  ): WebMonad[T] = {
    val monDefault: Option[Option[T]] = default.map{_.some.filter{_ != mon.empty}}
    askOption[T](innerMapping, key, monDefault)
      .map{_.getOrElse(mon.empty)}
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

  // Because I decided earlier on to make everything based off of JSON
  // I have to write silly things like this. TODO
  implicit val formatUnit: Format[Unit] = new Format[Unit] {
    def writes(u: Unit) = JsNull
    def reads(v: JsValue) = JsSuccess(())
  }

  protected def tell[A: HtmlShow](
    id: String,
    a: A 
  ): WebMonad[Unit] = {

    val unitMapping: Mapping[Unit] = Forms.of[Unit](new Formatter[Unit] {
      override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Unit] = Right(())

      override def unbind(key: String, value: Unit): Map[String, String] = Map.empty
    })

    formPage(id)(unitMapping, none[Unit]){  (path, form, r) =>
      implicit val request: Request[AnyContent] = r

      uniform.tell(id, form, path, a.showHtml)
    }
  }

  protected def askList[A](
    innerMapping: Mapping[A],
    id: String,
    min: Int = 0,
    max: Int = 100,
    default: List[A] = List.empty[A]
  )(implicit hs: HtmlShow[A], htmlForm: FormHtml[A], format: Format[A]): WebMonad[List[A]] =
    manyT[A](id, ask(innerMapping, _), min, max, default)

  protected def askBigText(
    id: String,
    default: Option[String] = None,
    constraints: List[(String, String => Boolean)] = Nil,
    errorOnEmpty: String = "error.required"
  ): WebMonad[String] = {

    def constraintMap[T](
      constraints: List[(String, T => Boolean)]
    ): List[Constraint[T]] =
      constraints.map{ case (error, pred) =>
        Constraint { t: T =>
          if (pred(t)) Valid else Invalid(Seq(ValidationError(error)))
        }
      }

    val mapping = text.verifying(errorOnEmpty, _.trim.nonEmpty).verifying(constraintMap(constraints) :_*)

    formPage(id)(mapping, default) { (path, b, r) =>
      implicit val request: Request[AnyContent] = r
      val fragment = uniform.fragments.bigtext(id, b)
      uniform.ask(id,  b, fragment, path)
    }
  }

  protected def journeyEnd(
    id: String,
    now: LocalDate = LocalDate.now,
    subheading: Option[Html] = None,
    whatHappensNext: Option[Html] = None,
    getTotal: Option[Html] = None
  ): WebMonad[Result] =
    webMonad{ (rid, request, path, db) =>
      implicit val r = request

      Future.successful {
        (id.some, path, db, Ok(uniform.journeyEnd(id, path, now, subheading, whatHappensNext, getTotal)).asLeft[Result])
      }
    }

  protected def end[A : HtmlShow, R](id: String, input: A): WebMonad[R] =
    webMonad{ (rid, request, path, db) =>
      implicit val r = request

      Future.successful {
        (id.some, path, db, Ok(uniform.end(id, path, input.showHtml)).asLeft[R])
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

    def confirmation(q: A): WebMonad[Boolean] =
      tell(s"remove-$id", q).map{_ => true}

    many[A](id, min, max, default, confirmation){ case (iid, minA, maxA, items) =>

      val mapping = nonEmptyText
        .verifying(s"$id.error.items.tooFew", a => a != "Done" || items.size >= min)
        .verifying(s"$id.error.items.tooMany", a => a != "Add" || items.size < max)

      formPage(id)(mapping) { (path, b, r) =>
        implicit val request: Request[AnyContent] = r
        uniform.many(id, b, items.map{_.showHtml}, path, min)
      }.imap(outf)(inf)
    }(wm)
  }

  def askPackSites(existingSites: List[Site], packs: Boolean): WebMonad[List[Site]] =
    manyT("packSitesActivity",
      ask(packagingSiteMapping,_)(packagingSiteForm, implicitly),
      default = existingSites,
      min = 1
    ) emptyUnless (packs)
}

