/*
 * Copyright 2024 HM Revenue & Customs
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

package sdil.uniform

import scala.language.implicitConversions

import ltbs.uniform._
import ltbs.uniform.common.web._
import ltbs.uniform.interpreters.playframework._
import play.api.libs.json._
import play.api.mvc.{AnyContent, Result}
import play.twirl.api.Html
import scala.concurrent._
import sdil.actions.AuthorisedRequest
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.ShortLivedHttpCaching
import uk.gov.hmrc.play.http.HeaderCarrierConverter

object DbFormat {

  val dbFormatter: Format[DB] = new Format[DB] {
    val mapFormatter: Format[Map[String, String]] = implicitly[Format[Map[String, String]]]
    override def writes(o: DB): JsValue =
      mapFormatter.writes(o.map {
        case (Nil, v)      => ("[]", v)
        case (List(""), v) => ("", v)
        case (k, v)        => (k.mkString("/"), v)
      })
    override def reads(json: JsValue): JsResult[DB] =
      mapFormatter.reads(json).map {
        _.map {
          case ("[]", v) => (List.empty[String], v)
          case ("", v)   => (List(""), v)
          case (k, v)    => ((k.replace("/", " /") + " ").split(" /").toList.map(_.trim), v)
        }
      }
  }

}

case class SaveForLaterPersistenceNew[Req <: play.api.mvc.Request[AnyContent]](
  idFunc: Req => String
)(
  journeyName: String,
  shortLiveCache: ShortLivedHttpCaching
)(
  implicit
  ec: ExecutionContext)
    extends PersistenceEngine[Req] {

  implicit val dbFormatter: Format[DB] = DbFormat.dbFormatter

  private implicit def requestToHc(implicit req: Req) = HeaderCarrierConverter.fromRequest(req)

  override def apply(request: Req)(f: DB ⇒ Future[(DB, Result)]): Future[Result] =
    for {
      db              <- dataGet()(request)
      (newDb, result) <- f(db)
      _               <- dataPut(newDb)(request)
    } yield result

  def dataGet()(
    implicit req: Req
  ): Future[DB] =
    shortLiveCache.fetchAndGetEntry[DB](idFunc(req), journeyName).map {
      _.getOrElse(Map.empty[List[String], String])
    }

  def dataPut(dataIn: DB)(
    implicit req: Req
  ): Future[Unit] =
    shortLiveCache.cache(idFunc(req), journeyName, dataIn).map { _ =>
      (())
    }

}
