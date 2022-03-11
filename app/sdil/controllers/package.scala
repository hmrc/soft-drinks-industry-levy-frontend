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

package sdil

import cats.implicits._
import ltbs.uniform.ask

import scala.concurrent.Future
import scala.language.implicitConversions
import izumi.reflect.Tag
import ltbs.uniform._
import validation.Rule
import sdil.models.Litreage

import java.time.LocalDate

package object controllers {
  implicit def future[A](a: A): Future[A] = Future.successful(a)

  val returnLiterageList = List(
    "own-brands-packaged-at-own-sites",
    "overclaim-warning",
    "packaged-as-a-contract-packer",
    "exemptions-for-small-producers",
    "brought-into-uk",
    "brought-into-uk-from-small-producers",
    "claim-credits-for-exports",
    "claim-credits-for-lost-damaged",
    "overclaim-warning2"
  )

  def askEmptyOption[A: Tag](
    id: String,
    default: Option[A] = None,
    validation: Rule[Option[A]] = Rule.alwaysPass[Option[A]])(implicit mon: cats.Monoid[A]) = {
    val newDefault = default.map {
      case e if e == mon.empty => none[A]
      case x                   => Some(x)
    }
    ask[Option[A]](id, newDefault, validation).map(_.getOrElse(mon.empty))
  }

  def askListSimple[A: izumi.reflect.Tag](
    key: String,
    subKey: String = "subKey",
    default: Option[List[A]] = None,
    listValidation: Rule[List[A]] = Rule.alwaysPass[List[A]],
    elementValidation: Rule[A] = Rule.alwaysPass[A]
  ) = askList[A](key, default, listValidation) {
    case (index: Option[Int], existing: List[A]) =>
      ask[A](s"$subKey", default = index.map(existing), validation = elementValidation)
  }

  def longTupToLitreage(inOpt: Option[(Long, Long)]): Option[Litreage] =
    inOpt match {
      case Some(in) => longTupToLitreage(in)
      case _        => None
    }

  def longTupToLitreage(in: (Long, Long)): Option[Litreage] =
    if (in.isEmpty) None else Litreage(in._1, in._2).some

  implicit val orderDate = new cats.Order[LocalDate] {
    def compare(x: LocalDate, y: LocalDate): Int = x.compareTo(y)
  }
}
