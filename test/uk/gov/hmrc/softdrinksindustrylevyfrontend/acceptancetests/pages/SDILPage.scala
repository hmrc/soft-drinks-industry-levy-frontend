package uk.gov.hmrc.softdrinksindustrylevyfrontend.acceptancetests.pages

import org.scalatest.selenium.WebBrowser
import org.scalatest.{FlatSpec, Matchers}
import uk.gov.hmrc.softdrinksindustrylevyfrontend.acceptancetests.generic.WebPage

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
