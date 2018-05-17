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

package sdil.forms

import play.api.data.Forms._
import play.api.data.Mapping
import play.api.data.validation.{Constraint, Invalid, Valid}
import play.api.libs.json.Json
import sdil.models.Address
import sdil.models.backend.{PackagingSite, Site, UkAddress, WarehouseSite}

import scala.util.Try

trait FormHelpers {
  private lazy val postcodeRegex = "^[A-Z]{1,2}[0-9][0-9A-Z]?\\s?[0-9][A-Z]{2}$|BFPO\\s?[0-9]{1,5}$"
  private lazy val specialRegex = """^[A-Za-z0-9 _]*[A-Za-z0-9][A-Za-z0-9 _]*$"""

  lazy val postcode: Mapping[String] = text.transform[String](_.toUpperCase.trim, s => s).verifying(Constraint { x: String =>
    x match {
      case "" => Invalid("error.postcode.empty")
      case pc if !pc.matches(specialRegex) => Invalid("error.postcode.special")
      case pc if !pc.matches(postcodeRegex) => Invalid("error.postcode.invalid")
      case _ => Valid
    }
  })

  lazy val addressMapping: Mapping[Address] = mapping(
    "line1" -> mandatoryAddressLine("line1"),
    "line2" -> mandatoryAddressLine("line2"),
    "line3" -> optionalAddressLine("line3"),
    "line4" -> optionalAddressLine("line4"),
    "postcode" -> postcode
  )(Address.apply)(Address.unapply)

//  lazy val siteJsonMapping: Mapping[Site] = text.transform[Site](s => Json.parse(s).as[Site], s => Json.toJson(s).toString)
  lazy val packagingSiteJsonMapping: Mapping[PackagingSite] =
    text.transform[PackagingSite](s => Json.parse(s).as[PackagingSite], s => Json.toJson(s).toString)
  lazy val warehouseSiteJsonMapping: Mapping[WarehouseSite] =
    text.transform[WarehouseSite](s => Json.parse(s).as[WarehouseSite], s => Json.toJson(s).toString)

  def oneOf(options: Seq[String], errorMsg: String): Mapping[String] = {
    //have to use optional, or the framework returns `error.required` when no option is selected
    optional(text).verifying(errorMsg, s => s.exists(options.contains)).transform(_.getOrElse(""), Some.apply)
  }

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

  lazy val tradingNameMapping: Mapping[String] = {
    text.transform[String](_.trim, s => s).verifying(optionalTradingNameConstraint)
  }

  private def optionalTradingNameConstraint: Constraint[String] = Constraint {
    case "" => Invalid("error.tradingName.required")
    case s if s.length > 160 => Invalid("error.tradingName.length")
    case s if !s.matches("""^[a-zA-Z0-9 '.&\\/]{1,160}$""") => Invalid("error.tradingName.invalid")
    case _ => Valid
  }

  def required(key: String): Constraint[String] = Constraint {
    case "" => Invalid(s"error.$key.required")
    case _ => Valid
  }

  lazy val mandatoryBoolean: Mapping[Boolean] = optional(boolean)
    .verifying("error.radio-form.choose-option", _.nonEmpty).transform(_.get, Some.apply)

  def combine[T](c1: Constraint[T], c2: Constraint[T]): Constraint[T] = Constraint { v =>
    c1.apply(v) match {
      case Valid => c2.apply(v)
      case i: Invalid => i
    }
  }

  lazy val litreage: Mapping[BigDecimal] = text
    .verifying("error.litreage.required", _.nonEmpty)
    .transform[String](_.replaceAll(",", ""), _.toString)
    .verifying("error.litreage.numeric", l => l.isEmpty || Try(BigDecimal.apply(l)).isSuccess) //don't try to parse empty string as a number
    .transform[BigDecimal](BigDecimal.apply, _.toString)
    .verifying("error.litreage.numeric", _.isWhole)
    .verifying("error.litreage.max", _ <= 9999999999999L)
    .verifying("error.litreage.min", _ >= 0)
}
