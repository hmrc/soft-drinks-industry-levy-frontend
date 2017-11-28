/*
 * Copyright 2017 HM Revenue & Customs
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

import play.api.data.Forms.{boolean, optional}
import play.api.data.Mapping

import scala.concurrent.Future

package object controllers {
  implicit def future[A](a: A): Future[A] = Future.successful(a)

  lazy val booleanMapping: Mapping[Boolean] =
    optional(boolean).verifying("sdil.form.radio.error", _.nonEmpty).
      transform(_.getOrElse(false), x => Some(x))

}
