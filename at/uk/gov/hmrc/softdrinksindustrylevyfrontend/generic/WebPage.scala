package uk.gov.hmrc.softdrinksindustrylevyfrontend.generic

import org.openqa.selenium.{Keys, WebElement}
import org.scalatest._
import org.scalatest.concurrent.{Eventually, PatienceConfiguration}
import org.scalatest.selenium.WebBrowser
import org.scalatest.time.{Millis, Seconds, Span}
import uk.gov.hmrc.softdrinksindustrylevyfrontend.util.{ImplicitWebDriverSugar, NavigationSugar}


trait WebPage extends org.scalatest.selenium.Page
  with Matchers
  with NavigationSugar
  with WebBrowser
  with Eventually
  with PatienceConfiguration
  with Assertions
  with ImplicitWebDriverSugar {

  override val url = ""

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(timeout = scaled(Span(5, Seconds)), interval = scaled(Span(500, Millis)))

  def isCurrentPage: Boolean = false

  def back(): Unit = clickOn("ButtonBack")

  def textField(id: String, value: String): Unit = {
    val elem = find(id)
    if (elem.isDefined) {
      val e = new TextField(elem.get.underlying)
      if (e.isDisplayed) e.value = value
    }
  }

  def passwordField(id: String, value: String): Unit = {
    val elem = find(id)
    if (elem.isDefined) {
      val e = new PasswordField(elem.get.underlying)
      if (e.isDisplayed) e.value = value
    }
  }

  def numberField(id: String, value: String): Unit = {
    val elem = find(id)
    if (elem.isDefined) {
      val e = new NumberField(elem.get.underlying)
      if (e.isDisplayed) e.value = value
    }
  }

  def pressKeys(value: Keys): Unit = {
    val e: WebElement = webDriver.switchTo.activeElement
    e.sendKeys(value)
  }

  def singleSel(id: String, value: String): Unit = {
    val elem = find(id)
    if (elem.isDefined) {
      val e = new SingleSel(elem.get.underlying)
      if (e.isDisplayed) e.value = value
    }
  }

  def checkHeader(heading: String, text: String) = {
    find(cssSelector(heading)).exists(_.text == text)
  }

}
