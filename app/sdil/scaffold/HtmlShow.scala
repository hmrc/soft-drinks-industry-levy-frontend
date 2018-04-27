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

import play.twirl.api.Html
import simulacrum._
import shapeless._
import shapeless.labelled._

import scala.language.implicitConversions

@typeclass trait HtmlShow[A] {
  def showHtml(in: A): Html
}

object HtmlShow {

  import ops._

  protected def instance[A](f: A => Html) = new HtmlShow[A] {
    def showHtml(in: A): Html = f(in)
  }

  implicit val showText = instance[String]{ Html(_) }

  implicit val showInt = instance[Int]{ i => Html(i.toString) }

  implicit def showOpt[A](implicit show: HtmlShow[A]) = instance[Option[A]] {
    case Some(x) => x.showHtml
    case None => Html("(empty)")
  }

  implicit def showList[A](implicit show: HtmlShow[A]) = instance[List[A]] { xs =>
    val inner = xs.map{ x => s"<li>${x.showHtml}</li>" }.mkString
    Html{ "<ul>" + inner + "</ul>" }
  }

  implicit def showHCons[K <: Symbol, H, T <: HList](
    implicit
      witness: Witness.Aux[K],
    hEncoder: Lazy[HtmlShow[H]],
    tEncoder: HtmlShow[T]
  ): HtmlShow[FieldType[K, H] :: T] = {
    val fieldName = witness.value.name
    instance { case (x::xs) =>
      Html(s"<dt>$fieldName</dt><dd>${hEncoder.value.showHtml(x)}</dd>${tEncoder.showHtml(xs)}")
    }
  }

  implicit val showHNil = instance[HNil] { _ => Html("") }

  implicit def genericShow[A, T](
    implicit generic: LabelledGeneric.Aux[A,T],
     hGenProvider: Lazy[HtmlShow[T]]
  ): HtmlShow[A] = instance { in =>
    Html (s"<dl>${hGenProvider.value.showHtml(generic.to(in))}</dl>")
  }

}
