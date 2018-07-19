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

package sdil.models

import java.time.{LocalDate => Date}
import play.api.i18n.Messages
import play.api.libs.json._

sealed trait FinancialLineItem {
  def date: Date
  def amount: BigDecimal
  def description(implicit messages: Messages): String = getClass.toString
}

case class ReturnCharge(period: ReturnPeriod, amount: BigDecimal) extends FinancialLineItem {

  val formatter = java.time.format.DateTimeFormatter.ofPattern("MMMM")

  override def description(implicit messages: Messages): String =
    Messages(
      "financiallineitem.returncharge",
      formatter.format(period.start),
      period.end.format(formatter)
    )
  def date = period.deadline
}

case class ReturnChargeInterest(date: Date, amount: BigDecimal) extends FinancialLineItem {
  override def description(implicit messages: Messages): String =
    Messages("financiallineitem.returnchargeinterest")
}

case class CentralAssessment(date: Date, amount: BigDecimal) extends FinancialLineItem {
  override def description(implicit messages: Messages): String =
    Messages("financiallineitem.centralassessment")
}
case class CentralAsstInterest(date: Date, amount: BigDecimal) extends FinancialLineItem {
  override def description(implicit messages: Messages): String =
    Messages("financiallineitem.centralasstinterest")
}
case class OfficerAssessment(date: Date, amount: BigDecimal) extends FinancialLineItem {
  override def description(implicit messages: Messages): String =
    Messages("financiallineitem.officerassessment")
}
case class OfficerAsstInterest(date: Date, amount: BigDecimal) extends FinancialLineItem {
  override def description(implicit messages: Messages): String =
    Messages("financiallineitem.officerasstinterest")
}
case class PaymentOnAccount(date: Date, reference: String, amount: BigDecimal) extends FinancialLineItem {
  override def description(implicit messages: Messages): String =
    Messages("financiallineitem.paymentonaccount", reference)
}
case class Unknown(date: Date, title: String, amount: BigDecimal) extends FinancialLineItem {
  override def description(implicit messages: Messages): String = title
}

object FinancialLineItem {

  implicit val formatPeriod: Format[ReturnPeriod] =
    Json.format[ReturnPeriod]

  implicit val formatter: Format[FinancialLineItem] =
    new Format[FinancialLineItem]
  {
    def reads(json: JsValue): JsResult[FinancialLineItem] =
      (json \ "type").as[String] match {
        case "ReturnCharge" => Json.format[ReturnCharge].reads(json)
        case "ReturnChargeInterest" => Json.format[ReturnChargeInterest].reads(json)
        case "CentralAssessment" => Json.format[CentralAssessment].reads(json)
        case "CentralAsstInterest" => Json.format[CentralAsstInterest].reads(json)
        case "OfficerAssessment" => Json.format[OfficerAssessment].reads(json)
        case "OfficerAsstInterest" => Json.format[OfficerAsstInterest].reads(json)
        case "PaymentOnAccount" => Json.format[PaymentOnAccount].reads(json)
        case "Unknown" => Json.format[Unknown].reads(json)
      }

    def writes(o: FinancialLineItem): JsValue = o match {
      case i: ReturnCharge => Json.format[ReturnCharge].writes(i).as[JsObject] + ("type" -> JsString("ReturnCharge"))
      case i: ReturnChargeInterest => Json.format[ReturnChargeInterest].writes(i).as[JsObject] + ("type" -> JsString("ReturnChargeInterest"))
      case i: CentralAssessment => Json.format[CentralAssessment].writes(i).as[JsObject] + ("type" -> JsString("CentralAssessment"))
      case i: CentralAsstInterest => Json.format[CentralAsstInterest].writes(i).as[JsObject] + ("type" -> JsString("CentralAsstInterest"))
      case i: OfficerAssessment => Json.format[OfficerAssessment].writes(i).as[JsObject] + ("type" -> JsString("OfficerAssessment"))
      case i: OfficerAsstInterest => Json.format[OfficerAsstInterest].writes(i).as[JsObject] + ("type" -> JsString("OfficerAsstInterest"))
      case i: PaymentOnAccount => Json.format[PaymentOnAccount].writes(i).as[JsObject] + ("type" -> JsString("PaymentOnAccount"))
      case i: Unknown => Json.format[Unknown].writes(i).as[JsObject] + ("type" -> JsString("Unknown"))
    }
  }
}
