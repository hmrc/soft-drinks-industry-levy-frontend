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

package sdil

import play.api.libs.json._
import sdil.models.backend.{PackagingSite, Site, WarehouseSite}
import sdil.models.retrieved.RetrievedSubscription

import scala.concurrent.Future
import scala.language.implicitConversions

package object controllers {
  implicit def future[A](a: A): Future[A] = Future.successful(a)
  implicit val siteWrites: Writes[Site] = new Writes[Site] {
    override def writes(site: Site) = site match {
      case packaging: PackagingSite => Json.toJson(packaging)
      case warehouse: WarehouseSite => Json.toJson(warehouse)
    }
  }
  implicit val retrievedSubscriptionFormat: Format[RetrievedSubscription] = Json.format[RetrievedSubscription]
}
