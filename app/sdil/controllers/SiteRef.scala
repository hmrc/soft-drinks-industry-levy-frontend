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

import sdil.forms.FormHelpers
import sdil.models.backend.Site

import scala.util.Try

trait SiteRef extends FormHelpers {

  def nextRef(original: Seq[Site], updated: Seq[Site]): String = (original, updated) match {
    case (Nil, Nil) => "1"
    case (o, Nil) => (maxRef(o) + 1).toString
    case (Nil, u) => (maxRef(u) + 1).toString
    case (o, u) => (maxRef(o).max(maxRef(u)) + 1).toString
  }

  private def maxRef(sites: Seq[Site]): Int = {
    sites.map(_.ref.fold(0)(x => Try(x.toInt).toOption.getOrElse(0))).max
  }
}
