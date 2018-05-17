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

package ltbs.play.scaffold

import java.time.LocalDate

import cats.implicits._
import enumeratum._
import play.api.data.Forms._
import play.api.data._
import play.api.i18n.Messages
import play.twirl.api.Html
import uk.gov.voa.play.form.ConditionalMappings.mandatoryIfTrue
import views.html.gdspages

trait GdsComponents {

  val bool: Mapping[Boolean] = optional(boolean)
    .verifying("error.required", _.isDefined)
    .transform(_.getOrElse(false),{x: Boolean => x.some})

  def innerOpt[A](key: String, innerMap: Mapping[A]): Mapping[Option[A]] =
    mapping(
      "outer" -> bool,
      "inner" -> mandatoryIfTrue(s"${key}.outer", innerMap)
    ){(_,_) match { case (outer, inner) => inner }
    }( a => (a.isDefined, a).some )

  implicit class RichEnum[A <: EnumEntry](e: Enum[A]) {
    lazy val possValues: Set[String] = e.values.map{_.toString}.toSet
    def single: Mapping[A] =
      nonEmptyText
        .verifying(possValues.contains(_))
        .transform(e.withName, _.toString)

    def set: Mapping[Set[A]] =
      list(nonEmptyText)
        .verifying(_.toSet subsetOf possValues)
        .transform(_.map{e.withName}.toSet,_.map(_.toString).toList)
  }

  // These implicits tell uniform how to render a form for a given mapping, later on I intend to
  // have these produced directly from the views via an SBT plugin, but this will only be possible
  // once uniform is moved to a separate library

  implicit val booleanForm = new FormHtml[Boolean] {
    def asHtmlForm(key: String, form: Form[Boolean])(implicit messages: Messages): Html = {
      gdspages.fragments.boolean(key, form)
    }
  }

  implicit val stringForm = new FormHtml[String] {
    def asHtmlForm(key: String, form: Form[String])(implicit messages: Messages): Html = {
      gdspages.fragments.string(key, form)
    }
  }

  implicit val dateForm = new FormHtml[LocalDate] {
    def asHtmlForm(key: String, form: Form[LocalDate])(implicit messages: Messages): Html = {
      gdspages.fragments.date(key, form)
    }
  }

  implicit val htmlShowHtml = new HtmlShow[Html] {
    def showHtml(in: Html): Html = in
  }

  implicit val dateShow = new HtmlShow[LocalDate] {
    def showHtml(in: LocalDate): Html = Html(in.toString) // ISO YYYY-MM-DD
  }

}
