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

import java.time.LocalDate

import cats.implicits._
import play.api.data.Forms._
import play.api.data._
import play.api.data.validation.{Constraint, Constraints, Invalid, Valid}
import play.api.i18n.Messages
import play.api.libs.functional.syntax._
import play.api.libs.functional.syntax.unlift
import play.api.libs.json._
import play.twirl.api.Html
import sdil.controllers.ContactDetailsForm.{combine, required}
import sdil.models._
import sdil.models.backend.{Site, UkAddress}
import views.html.uniform

import scala.util.Try

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
      uniform.fragments.litreage(key, form, true)
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

  lazy val contactDetailsMapping: Mapping[ContactDetails] = mapping(
    "fullName" -> text.verifying(Constraint { x: String =>
      x match {
        case "" => Invalid("error.fullName.required")
        case name if name.length > 40 => Invalid("error.fullName.over")
        case name if !name.matches("^[a-zA-Z &`\\-\\'\\.^]{1,40}$") =>
          Invalid("error.fullName.invalid")
        case _ => Valid
      }
    }),
    "position" -> text.verifying(Constraint { x: String =>
      x match {
        case "" => Invalid("error.position.required")
        case position if position.length > 155 => Invalid("error.position.over")
        case position if !position.matches("^[a-zA-Z &`\\-\\'\\.^]{1,155}$") =>
          Invalid("error.position.invalid")
        case _ => Valid
      }
    }),
    "phoneNumber" -> text.verifying(Constraint { x: String =>
      x match {
        case "" => Invalid("error.phoneNumber.required")
        case phone if phone.length > 24 => Invalid("error.phoneNumber.over")
        case phone if !phone.matches("^[A-Z0-9 )/(\\-*#+]{1,24}$") =>
          Invalid("error.phoneNumber.invalid")
        case _ => Valid
      }
    }),
    "email" -> text
      .verifying("error.email.over", _.length <= 132)
      .verifying(combine(required("email"), Constraints.emailAddress))
  )(ContactDetails.apply)(ContactDetails.unapply)

  lazy val startDate: Mapping[LocalDate] = tuple(
    "day" -> numeric("day").verifying("error.start-day.invalid", d => d > 0 && d <= 31),
    "month" -> numeric("month").verifying("error.start-month.invalid", d => d > 0 && d <= 12),
    "year" -> numeric("year").verifying("error.start-year.invalid", d => d >= 1900 && d < 2100)
  ).verifying("error.date.invalid", x => x match {
    case (d, m, y) => Try(LocalDate.of(y, m, d)).isSuccess
  })
    .transform({ case (d, m, y) => LocalDate.of(y, m, d) }, d => (d.getDayOfMonth, d.getMonthValue, d.getYear))

  def numeric(key: String): Mapping[Int] = text
    .verifying(s"error.$key.required", _.nonEmpty)
    .verifying("error.number", v => v.isEmpty || Try(v.toInt).isSuccess)
    .transform[Int](_.toInt, _.toString)

  val orgTypes: List[String] = List(
    "partnership",
    "limitedCompany",
    "limitedLiabilityPartnership",
    "unincorporatedBody"
  )

  val producerTypes: List[String] = List(
    "large",
    "small",
    "not"
  )

  lazy val warehouseSiteMapping: Mapping[Site] = mapping(
    "address" -> ukAddressMapping,
    "tradingName" -> optional(tradingNameMapping)
  ) { (a, b) => Site.apply(a, none, b, none) }(Site.unapply(_).map { case (address, _, tradingName, _) =>
    (address, tradingName)
  })

  lazy val tradingNameMapping: Mapping[String] = {
    text.transform[String](_.trim, identity).verifying(optionalTradingNameConstraint)
  }

  private def optionalTradingNameConstraint: Constraint[String] = Constraint {
    case s if s.length > 160 => Invalid("error.tradingName.length")
    case s if !s.matches("""^[a-zA-Z0-9 '.&\\/]$""") => Invalid("error.tradingName.invalid")
    case _ => Valid
  }

  lazy val packagingSiteMapping: Mapping[Site] = mapping(
    "address" -> ukAddressMapping
  ) { a => Site.apply(a, none, none, none) }(Site.unapply(_).map(x => x._1))

  private val ukAddressMapping: Mapping[UkAddress] =
    addressMapping.transform(UkAddress.fromAddress, Address.fromUkAddress)

  lazy val addressMapping: Mapping[Address] = mapping(
    "line1" -> mandatoryAddressLine("line1"),
    "line2" -> mandatoryAddressLine("line2"),
    "line3" -> optionalAddressLine("line3"),
    "line4" -> optionalAddressLine("line4"),
    "postcode" -> postcode
  )(Address.apply)(Address.unapply)

  private def mandatoryAddressLine(key: String): Mapping[String] = {
    text.transform[String](_.trim, s => s).verifying(combine(required(key), optionalAddressLineConstraint(key)))
  }

  private def optionalAddressLine(key: String): Mapping[String] = {
    text.transform[String](_.trim, s => s).verifying(optionalAddressLineConstraint(key))
  }

  private def optionalAddressLineConstraint(key: String): Constraint[String] = Constraint {
    case a if !a.matches("""^[A-Za-z0-9 \-,.&'\/]*$""") => Invalid(s"error.$key.invalid")
    case b if b.length > 35 => Invalid(s"error.$key.over")
    case _ => Valid
  }

  private def postcode: Mapping[String] = {
    val postcodeRegex = "^[A-Z]{1,2}[0-9][0-9A-Z]?\\s?[0-9][A-Z]{2}$|BFPO\\s?[0-9]{1,5}$"
    val specialRegex = """^[A-Za-z0-9 _]*[A-Za-z0-9][A-Za-z0-9 _]*$"""

    text.transform[String](_.toUpperCase.trim, identity)
      .verifying(Constraint { x: String =>
        x match {
          case "" => Invalid("error.postcode.empty")
          case pc if !pc.matches(specialRegex) => Invalid("error.postcode.special")
          case pc if !pc.matches(postcodeRegex) => Invalid("error.postcode.invalid")
          case _ => Valid
        }
      }
      )
  }

  def longTupToLitreage(in: (Long,Long)): Option[Litreage] =
    if (in.isEmpty) None else Litreage(in._1, in._2).some

}
