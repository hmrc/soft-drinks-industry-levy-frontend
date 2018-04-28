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
import ltbs.play.scaffold.HtmlShow
import ltbs.play.scaffold.webmonad._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import scala.collection.mutable.{Map => MMap}
import scala.concurrent._
import play.api.data.Forms._
import play.api.mvc.{Action, AnyContent, Request, Result}
import sdil.config.AppConfig
import sdil.models._
import views.html.gdspages
import cats.implicits._
import HtmlShow.ops._

trait JunkPersistence {

  implicit def ec: ExecutionContext

  // crappy persistence, but good for development
  private val data = MMap.empty[String,Map[String,JsValue]]

  protected def dataGet(session: String): Future[Map[String, JsValue]] =
    data.getOrElse(session, Map.empty[String,JsValue]).pure[Future]

  protected def dataPut(session: String, dataIn: Map[String, JsValue]): Unit = 
    data(session) = dataIn
}

trait SdilWMController extends WebMonadController with JunkPersistence {

  implicit def config: AppConfig

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

  protected def askAddress(id: String): WebMonad[Address] = {
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

  protected def manyT[A](id: String, wm: String => WebMonad[A], min: Int = 0, max: Int = 100)(implicit hs: HtmlShow[A], format: Format[A]): WebMonad[List[A]] = {
    
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


}
