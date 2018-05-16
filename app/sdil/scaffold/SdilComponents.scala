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

import play.api.data._
import play.api.i18n.Messages
import play.api.mvc.{AnyContent, Request}
import play.twirl.api.Html
import sdil.models._
import sdil.models.backend.{PackagingSite, Site, WarehouseSite}
import views.html.gdspages

trait SdilComponents {

  implicit val addressForm = new FormHtml[Address] {
    def asHtmlForm(key: String, form: Form[Address])(implicit messages: Messages): Html = {
      gdspages.fragments.address(key, form)
    }
  }

  implicit val smallProducerForm = new FormHtml[SmallProducer] {
    def asHtmlForm(key: String, form: Form[SmallProducer])(implicit messages: Messages): Html = {
      gdspages.fragments.smallProducer(key, form)
    }
  }

  implicit val litreageForm = new FormHtml[(Long,Long)] {
    def asHtmlForm(key: String, form: Form[(Long,Long)])(implicit messages: Messages): Html = {
      gdspages.fragments.litreage(key, form)
    }
  }

  implicit val siteForm = new FormHtml[Site] {
    def asHtmlForm(key: String, form: Form[Site])(implicit messages: Messages): Html = {
      gdspages.fragments.site(key, form)
    }
  }

  implicit val packagingSiteForm = new FormHtml[PackagingSite] {
    def asHtmlForm(key: String, form: Form[PackagingSite])(implicit messages: Messages): Html = {
      gdspages.fragments.packagingSite(key, form)
    }
  }

  implicit val warehouseSiteForm = new FormHtml[WarehouseSite] {
    def asHtmlForm(key: String, form: Form[WarehouseSite])(implicit messages: Messages): Html = {
      gdspages.fragments.warehouseSite(key, form)
    }
  }

  implicit def htmlShow[A <: Site] = new HtmlShow[A] {
    override def showHtml(in: A) = Html(in.getLines.mkString("<br />"))
  }

  implicit val contactDetailsForm = new FormHtml[ContactDetails] {
    def asHtmlForm(key: String, form: Form[ContactDetails])(implicit messages: Messages): Html = {
      gdspages.fragments.contactdetails(key, form)
    }
  }

  implicit val optBoolForm = new FormHtml[Option[Boolean]] {
    def asHtmlForm(key: String, form: Form[Option[Boolean]])(implicit messages: Messages): Html = {
      gdspages.fragments.optBoolean(key, form)
    }
  }

  // implicit def optFormHtml[A](implicit inner: FormHtml[A]): FormHtml[Option[A]] = {
  //   def asHtmlForm(key: String, form: Form[Option[A]])(implicit messages: Messages): Html = {
  //     val innerHtml = inner.asHtmlForm(s"${key}.inner", form(s"${key}.inner"))
  //     gdspages.fragments.innerOpt(key, form, innerHtml)
  //   }
  // }

  implicit val addressHtml: HtmlShow[Address] =
    HtmlShow.instance { address =>
      val lines = address.nonEmptyLines.mkString("<br />")
      Html(s"<div>$lines</div>")
    }

  implicit val siteHtml: HtmlShow[Site] = HtmlShow.instance { site =>
    HtmlShow[Address].showHtml(Address.fromUkAddress(site.address))
  }

}
