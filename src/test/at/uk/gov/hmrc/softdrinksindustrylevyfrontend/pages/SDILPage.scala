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

package uk.gov.hmrc.softdrinksindustrylevyfrontend.pages

import org.scalatest.selenium.WebBrowser
import org.scalatest.{FlatSpec, Matchers}
import uk.gov.hmrc.softdrinksindustrylevyfrontend.generic.WebPage

trait SDILPage extends FlatSpec with Matchers with WebBrowser with WebPage {

  def navigateTo(baseUrl: String) = {
    resetGlobalVariables()
    deleteAllCookies()
    go to s"$baseUrl/pay-what-you-owe-in-instalments"
  }

  override val url: String = ""

  override def back() = click on find(xpath(".//*[@class='back-link']")).get

  def buttonnext() = clickOn("ButtonNext")

  def submit() = clickOn("Submit")

  def continue() = click on find(xpath(".//*[@type='submit' and contains(text(),'Continue')]")).get

  def agreeAndContinue() = click on find(xpath(".//*[@type='submit' and contains(text(),'Agree and continue')]")).get

  def confirm() = click on find(xpath("//*[@value='Confirm']")).get

  def confirmPayment() = click on find(xpath(".//*[@type='submit' and contains(text(),'Confirm payments')]")).get

  def signIn() = click on find(xpath(".//*[@class='button ']")).get //TODO

  def recalculate() = click on find(xpath(".//*[@type='submit' and contains(text(),'Recalculate')]")).get

  def resetGlobalVariables() = {
  }

}
