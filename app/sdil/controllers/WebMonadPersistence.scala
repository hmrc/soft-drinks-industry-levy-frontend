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

import scala.concurrent._
import play.api.libs.json._
import scala.collection.mutable.{Map => MMap}

/** WebMonads read in all their data at the start of the interaction with the
  * user and write it all out again at the end.
  * A major limitation at the moment is that this has to be Json values. This
  * was a bad design decision as if I'd used plain serialisation or pickling
  * then the programmer building the journey wouldn't have needed to worry about
  * writing Format converters.
  * The store should be tolerant of failure - the structure of objects is likely
  * to change during development and it's usually fine to just ask the user again.
  */
trait WebMonadPersistence {
  def get: Future[Map[String, JsValue]]
  def update(data: Map[String, JsValue]): Future[Unit]
  def clear: Future[Unit] = update(Map.empty)
}

/** A non-presistent persistence engine - fast to develop in (store gets purged on
  * recompile), but guaranteed to bring shame and ruin should you actually dare
  * to use this in production. Basically a thin wrapper around a mutable map.
  */
class AJunkPersistence extends WebMonadPersistence {
  private var data: Map[String,JsValue] = Map.empty[String,JsValue]

  def get: Future[Map[String, JsValue]] =
    Future.successful(data)

  def update(dataIn: Map[String, JsValue]): Future[Unit] =
    Future.successful(data = dataIn)

}

