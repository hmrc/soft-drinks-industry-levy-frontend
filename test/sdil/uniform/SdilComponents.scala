/*
 * Copyright 2020 HM Revenue & Customs
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

package uniform

import java.time.LocalDate

import ltbs.play.scaffold.SdilComponents
import play.api.data.Form
import play.api.test.FakeRequest
import sdil.controllers.ControllerSpec
import sdil.models.backend.{Site, UkAddress}
import sdil.models.{Address, Litreage}

class SdilComponents extends ControllerSpec {

  val address: Address = Address("line1", "line2", "line3", "line4", "AL1 1UJ")
  val uKAddress: UkAddress = UkAddress(List("ukAddr1", "ukAddr2"), "WD25 7HQ")
  val litreage: Litreage = Litreage(BigDecimal(1.24), BigDecimal(576.89))
  val requestContactDetails = FakeRequest().withFormUrlEncodedBody(
    "fullName"    -> "Bill Gates",
    "position"    -> "CEO retired",
    "phoneNumber" -> "07867654567",
    "email"       -> "billgates@microsoft.com"
  )
  val reqContactDetailsMissing = FakeRequest().withFormUrlEncodedBody(
    "fullName"    -> "",
    "position"    -> "",
    "phoneNumber" -> "",
    "email"       -> ""
  )
  val reqContactDetailsWrongLengths = FakeRequest().withFormUrlEncodedBody(
    "fullName"    -> "Morethan 40 chars -- rgfrefrefregvregvregre",
    "position"    -> "Morethan 150 chars -- sdgfdsgfdsgfdsgggggdfgdfgfgfgfgfdgfdgdfgfdgfdgdfgdfgdfgdfgdfgdfgdfgdfgdfgdfgdfgdfgfgfdggfdgdfgdfgdfgrgfrefrefregvregvregrhjtyjtyjtyhjtyhjtyhjtyhrtye",
    "phoneNumber" -> "546546546565465465465464634",
    "email"       -> "Morethan 132 chars -- sdgfdsgfdsgfdsgggggdfgdfgfgfgfgfdgfdgdfgfdgfdgdfgdfgdfgdfgdfgdfgdfgdfgdfgdfgdfgdfgfgfdggfdgdfgdfgdfgrgfrefrefregvregvregre"
  )
  val reqContactDetailsWrongChars = FakeRequest().withFormUrlEncodedBody(
    "fullName"    -> "&*()ghghjgjjj",
    "position"    -> "&*()ghghjgjjj",
    "phoneNumber" -> "&*()ghghjgjjj",
    "email"       -> "&*()ghghjgjjj"
  )

  val formContactDetails = Form(SdilComponents.contactDetailsMapping)
  val wareHouseSiteMapping = Form(SdilComponents.warehouseSiteMapping)

  "A SdilComponent Call for contactDetails correctly bind" in {

    SdilComponents.addressHtml.showHtml(address)
    SdilComponents.contactDetailsForm
      .asHtmlForm("fail data", formContactDetails.bindFromRequest()(requestContactDetails))
    SdilComponents.contactDetailsForm
      .asHtmlForm("fail data", formContactDetails.bindFromRequest()(reqContactDetailsMissing))
    SdilComponents.contactDetailsForm
      .asHtmlForm("fail data", formContactDetails.bindFromRequest()(reqContactDetailsWrongLengths))
    SdilComponents.contactDetailsForm
      .asHtmlForm("fail data", formContactDetails.bindFromRequest()(reqContactDetailsWrongChars))

    val dateMap = Map("day" -> "01", "month" -> "02", "year" -> "2019")

    val siteObj = Site(uKAddress, Some("refOption"), Some("Arcade tradingName"), Some(LocalDate.now()))

    val dayMissingMap = dateMap + ("day"            -> "")
    val monthMissingMap = dateMap + ("month"        -> "")
    val yearMissingMap = dateMap + ("year"          -> "")
    val dayAndMonthMissingMap = dateMap + ("day"    -> "") + ("month" -> "")
    val dayAndYearMissingMap = dateMap + ("day"     -> "") + ("year" -> "")
    val monthAndYearMissingMap = dateMap + ("month" -> "") + ("year" -> "")
    val allMissingMap = dateMap + ("day"            -> "") + ("month" -> "") + ("year" -> "")
    val invalidDayMap = dateMap + ("day"            -> "567")

    SdilComponents.startDate.bind(dateMap).toString
    SdilComponents.startDate.bind(dayMissingMap)
    SdilComponents.startDate.bind(monthMissingMap)
    SdilComponents.startDate.bind(yearMissingMap)
    SdilComponents.startDate.bind(dayAndMonthMissingMap)
    SdilComponents.startDate.bind(dayAndYearMissingMap)
    SdilComponents.startDate.bind(monthAndYearMissingMap)
    SdilComponents.startDate.bind(allMissingMap)
    SdilComponents.startDate.bind(invalidDayMap)

    SdilComponents.cancelRegDate.bind(dateMap).toString
    SdilComponents.cancelRegDate.bind(dayMissingMap)
    SdilComponents.cancelRegDate.bind(monthMissingMap)
    SdilComponents.cancelRegDate.bind(yearMissingMap)
    SdilComponents.cancelRegDate.bind(dayAndMonthMissingMap)
    SdilComponents.cancelRegDate.bind(dayAndYearMissingMap)
    SdilComponents.cancelRegDate.bind(monthAndYearMissingMap)
    SdilComponents.cancelRegDate.bind(allMissingMap)
    SdilComponents.cancelRegDate.bind(invalidDayMap)

    SdilComponents.numeric("litreage").bind(Map("text" -> "2.35"))
    SdilComponents.siteProgressiveRevealHtml.showHtml(siteObj)
    SdilComponents.showLitreage.showHtml(litreage)
    SdilComponents.longTupToLitreage((20L, 505L))

    1 mustBe 1
  }
}
