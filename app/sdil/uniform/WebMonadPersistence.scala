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

package sdil.uniform

import cats.implicits._
import play.api.libs.json._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.ShortLivedHttpCaching

import scala.collection.mutable.{Map => MMap}
import scala.concurrent._
import uk.gov.hmrc.uniform._

case class SessionCachePersistence(
  journeyName: String,
  keystore: uk.gov.hmrc.http.cache.client.SessionCache
)(implicit
    ec: ExecutionContext,
  hc: HeaderCarrier
) extends Persistence {
  def dataGet(session: String): Future[Map[String, JsValue]] =
    keystore.fetchAndGetEntry[Map[String, JsValue]](journeyName).map{
      _.getOrElse(Map.empty)
    }

  def dataPut(session: String, dataIn: Map[String, JsValue]): Unit =
    keystore.cache(journeyName, dataIn)

}

case class SaveForLaterPersistence(
  journeyName: String,
  userId: String,
  shortLiveCache: ShortLivedHttpCaching
)(implicit
    ec: ExecutionContext,
  hc: HeaderCarrier
) extends Persistence {
  def dataGet(session: String): Future[Map[String, JsValue]] = {
    shortLiveCache.fetchAndGetEntry[Map[String, JsValue]](userId, journeyName).map{
      _.getOrElse(Map.empty)
    }
  }

  def dataPut(session: String, dataIn: Map[String, JsValue]): Unit =
    shortLiveCache.cache(userId, journeyName, dataIn)

}
