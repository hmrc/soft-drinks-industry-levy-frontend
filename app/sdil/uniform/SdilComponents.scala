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

import cats.implicits._
import play.api.data.Forms._
import play.api.data._
import play.api.data.validation.{Constraint, Invalid, Valid}
import play.api.i18n.Messages
import play.api.libs.functional.syntax._
import play.api.libs.functional.syntax.unlift
import play.api.libs.json._
import play.twirl.api.Html
import sdil.models._
import sdil.models.backend.{Site, UkAddress}
import views.html.uniform

object SdilComponents {

  implicit val addressForm = new FormHtml[Address] {
    def asHtmlForm(key: String, form: Form[Address])(implicit messages: Messages): Html = {
      uniform.fragments.address(key, form)
    }
  }

  implicit val smallProducerForm = new FormHtml[SmallProducer] {
    def asHtmlForm(key: String, form: Form[SmallProducer])(implicit messages: Messages): Html = {
      uniform.fragments.smallProducer(key, form)
    }
  }

  implicit val litreageForm = new FormHtml[(Long,Long)] {
    def asHtmlForm(key: String, form: Form[(Long,Long)])(implicit messages: Messages): Html = {
      uniform.fragments.litreage(key, form)
    }
  }

  implicit val siteForm = new FormHtml[Site] {
    def asHtmlForm(key: String, form: Form[Site])(implicit messages: Messages): Html = {
      uniform.fragments.site(key, form)
    }
  }

  val packagingSiteForm = new FormHtml[Site] {
    def asHtmlForm(key: String, form: Form[Site])(implicit messages: Messages): Html = {
      uniform.fragments.packagingSite(key, form)
    }
  }

  val warehouseSiteForm = new FormHtml[Site] {
    def asHtmlForm(key: String, form: Form[Site])(implicit messages: Messages): Html = {
      uniform.fragments.warehouseSite(key, form)
    }
  }

  implicit def htmlShow: HtmlShow[Site] = new HtmlShow[Site] {
    override def showHtml(in: Site): Html = Html(  in.getLines.mkString("<br />"))
  }

  implicit val contactDetailsForm = new FormHtml[ContactDetails] {
    def asHtmlForm(key: String, form: Form[ContactDetails])(implicit messages: Messages): Html = {
      uniform.fragments.contactdetails(key, form)
    }
  }

  implicit val addressHtml: HtmlShow[Address] =
    HtmlShow.instance { address =>
      val lines = address.nonEmptyLines.mkString("<br />")
      Html(s"<div>$lines</div>")
    }

  implicit def showLitreage(implicit messages: Messages): HtmlShow[Litreage] = new HtmlShow[Litreage]{
    def showHtml(l: Litreage): Html = l match {
      case Litreage(lower, higher) => Html(
        Messages("sdil.declaration.low-band") + f": $lower%,.0f" +
          "<br>" +
          Messages("sdil.declaration.high-band") + f": $higher%,.0f"
      )
    }
  }

  implicit val longTupleFormatter: Format[(Long, Long)] = (
    (JsPath \ "lower").format[Long] and
      (JsPath \ "higher").format[Long]
    )((a: Long, b: Long) => (a,b), unlift({x: (Long,Long) => Tuple2.unapply(x)}))

}
