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

package sdil.uniform

import ltbs.uniform.interpreters.playframework.{DB, PersistenceEngine}
import play.api.libs.json._
import play.api.mvc.{AnyContent, Result}
import sdil.actions.AuthorisedRequest
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.ShortLivedHttpCaching

import scala.concurrent._
import uk.gov.hmrc.uniform._

case class SessionCachePersistence(
  journeyName: String,
  keystore: uk.gov.hmrc.http.cache.client.SessionCache
)(
  implicit
  ec: ExecutionContext,
  hc: HeaderCarrier)
    extends Persistence {
  def dataGet(session: String): Future[Map[String, JsValue]] =
    keystore.fetchAndGetEntry[Map[String, JsValue]](journeyName).map {
      _.getOrElse(Map.empty)
    }

  def dataPut(session: String, dataIn: Map[String, JsValue]): Unit = {
    keystore.cache(journeyName, dataIn)
    ()
  }

}

case class SaveForLaterPersistence(
  journeyName: String,
  userId: String,
  shortLiveCache: ShortLivedHttpCaching
)(
  implicit
  ec: ExecutionContext,
  hc: HeaderCarrier)
    extends Persistence {
  def dataGet(session: String): Future[Map[String, JsValue]] =
    shortLiveCache.fetchAndGetEntry[Map[String, JsValue]](userId, journeyName).map {
      _.getOrElse(Map.empty)
    }

  def dataPut(session: String, dataIn: Map[String, JsValue]): Unit = {
    shortLiveCache.cache(userId, journeyName, dataIn)
    ()
  }

}

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

case class SaveForLaterPersistenceNew(
  journeyName: String,
  userId: String,
  shortLiveCache: ShortLivedHttpCaching
)(
  implicit
  ec: ExecutionContext,
  hc: HeaderCarrier)
    extends PersistenceEngine[AuthorisedRequest[AnyContent]] {

  implicit val dbFormatter: Format[DB] = DbFormat.dbFormatter

  override def apply(request: AuthorisedRequest[AnyContent])(f: DB ⇒ Future[(DB, Result)]): Future[Result] = {

    val userId = request.internalId

    for {
      db              <- dataGet(userId)
      (newDb, result) <- f(db)
      _               <- dataPut(userId, newDb)
    } yield result

  }

  def dataGet(session: String): Future[DB] =
    shortLiveCache.fetchAndGetEntry[DB](userId, journeyName).map {
      _.getOrElse(Map.empty[List[String], String])
    }

  def dataPut(session: String, dataIn: DB): Future[Unit] =
    shortLiveCache.cache(userId, journeyName, dataIn).map { _ =>
      (())
    }

}
