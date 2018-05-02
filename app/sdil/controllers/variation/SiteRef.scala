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

package sdil.controllers.variation

import play.api.data.Form
import play.api.data.Forms._
import sdil.forms.FormHelpers
import sdil.models.Sites
import sdil.models.backend.Site
import uk.gov.voa.play.form.ConditionalMappings.mandatoryIfTrue

trait SiteRef extends FormHelpers {

  def nextRef(original: Seq[Site], updated: Seq[Site]): String = (original, updated) match {
    case (Nil, Nil) => "1"
    case (o, Nil) => (maxRef(o) + 1).toString
    case (Nil, u) => (maxRef(u) + 1).toString
    case (o, u) => (maxRef(o).max(maxRef(u)) + 1).toString
  }

  private def maxRef(sites: Seq[Site]): Int = {
    sites.map(_.ref.fold(0)(_.toInt)).max
  }

  val initialForm: Form[Sites] = Form(
    mapping(
      "additionalSites" -> ignored(Seq.empty[Site]),
      "addAddress" -> mandatoryBoolean,
      "tradingName" -> optional(tradingNameMapping),
      "additionalAddress" -> mandatoryIfTrue("addAddress", addressMapping)
    )(Sites.apply)(Sites.unapply)
  )

}
