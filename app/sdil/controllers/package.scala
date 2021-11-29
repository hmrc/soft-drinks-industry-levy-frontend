/*
 * Copyright 2021 HM Revenue & Customs
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

import cats.implicits.none
import izumi.reflect.Tag
import ltbs.uniform.ask

import scala.concurrent.Future
import scala.language.implicitConversions
import izumi.reflect.Tag
import ltbs.uniform._, validation.Rule

package object controllers {
  implicit def future[A](a: A): Future[A] = Future.successful(a)

  val returnLiterageList = List(
    "own-brands-packaged-at-own-sites",
    "packaged-as-a-contract-packer",
    "exemptions-for-small-producers",
    "brought-into-uk",
    "brought-into-uk-from-small-producers",
    "claim-credits-for-exports",
    "claim-credits-for-lost-damaged"
  )

  def askEmptyOption[A: Tag](id: String, default: Option[A] = None)(implicit mon: cats.Monoid[A]) = {
    val newDefault = default.map {
      case e if e == mon.empty => none[A]
      case x                   => Some(x)
    }
    ask[Option[A]](id, newDefault).map(_.getOrElse(mon.empty))
  }

  def askListSimple[A: izumi.reflect.Tag](
    key: String,
    default: Option[List[A]] = None,
    validation: Rule[List[A]] = Rule.alwaysPass[List[A]]
  ) = askList[A](key, default, validation) {
    case (index: Option[Int], existing: List[A]) =>
      ask[A]("element", default = index.map(existing))
  }

}
