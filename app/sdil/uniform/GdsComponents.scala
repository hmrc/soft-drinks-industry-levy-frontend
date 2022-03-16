/*
 * Copyright 2022 HM Revenue & Customs
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

package sidl.uniform

import cats.Monoid
import java.time.LocalDate

import cats.implicits._
import enumeratum._
import play.api.data.Forms._
import play.api.data._
import play.api.i18n.Messages
import play.twirl.api.Html
import uk.gov.voa.play.form.ConditionalMappings.mandatoryIfTrue
import views.html.uniform

import scala.util.Try

object GdsComponents {

  def bool(key: String = ""): Mapping[Boolean] =
    optional(boolean)
      .verifying(s"error.radio-form.choose-option.$key", _.isDefined)
      .transform(_.getOrElse(false), { x: Boolean =>
        x.some
      })

  def innerOptEmpty[A](key: String, innerMap: Mapping[A])(implicit mon: Monoid[A]): Mapping[A] =
    mapping(
      "outer" -> bool(),
      "inner" -> mandatoryIfTrue(s"$key.outer", innerMap)
    ) {
      (_, _) match {
        case (true, inner) => inner.get
        case (false, _)    => mon.empty
      }
    }(a => (a == mon.empty, a.some.filter(_ != mon.empty)).some)

  implicit class RichEnum[A <: EnumEntry](e: Enum[A]) {
    lazy val possValues: Set[String] = e.values.map { _.toString }.toSet
    def single: Mapping[A] =
      nonEmptyText
        .verifying(possValues.contains(_))
        .transform(e.withName, _.toString)

    def set: Mapping[Set[A]] =
      list(nonEmptyText)
        .verifying(_.toSet subsetOf possValues)
        .transform(_.map { e.withName }.toSet, _.map(_.toString).toList)
  }

  // These implicits tell uniform how to render a form for a given mapping, later on I intend to
  // have these produced directly from the views via an SBT plugin, but this will only be possible
  // once uniform is moved to a separate library

  def oneOf(
    options: Seq[String],
    errorMsg: String = "error.required"
  ): Mapping[String] =
    optional(text)
      .verifying(errorMsg, s => s.exists(options.contains))
      .transform(_.getOrElse(""), Some.apply)

  val dateMapping: Mapping[LocalDate] = tuple(
    "day"   -> number(1, 31),
    "month" -> number(1, 12),
    "year"  -> number
  ).verifying("error.date.invalid", _ match {
      case (d, m, y) => Try(LocalDate.of(y, m, d)).isSuccess
    })
    .transform(
      { case (d, m, y) => LocalDate.of(y, m, d) },
      d => (d.getDayOfMonth, d.getMonthValue, d.getYear)
    )

  def litreage(key: String): Mapping[Long] =
    text
      .verifying(s"error.litreage.required.$key", _.nonEmpty)
      .transform[String](_.replaceAll(",", ""), _.toString)
      .verifying("error.litreage.numeric", l => Try(BigDecimal.apply(l)).isSuccess)
      .transform[BigDecimal](BigDecimal.apply, _.toString)
      .verifying("error.litreage.numeric", _.isWhole)
      .verifying(s"error.litreage.max.$key", _ <= 9999999999999L)
      .verifying("error.litreage.min", _ >= 0)
      .transform[Long](_.toLong, BigDecimal.apply)

  def litreagePair: Mapping[(Long, Long)] =
    tuple("lower" -> litreage("lower"), "higher" -> litreage("higher"))

}
