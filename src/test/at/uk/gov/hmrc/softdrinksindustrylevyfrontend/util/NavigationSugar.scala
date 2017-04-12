package uk.gov.hmrc.softdrinksindustrylevyfrontend.util

import java.awt.Robot

import org.openqa.selenium.support.ui.{ExpectedCondition, ExpectedConditions, WebDriverWait}
import org.openqa.selenium.{By, WebDriver, WebElement}
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.selenium.WebBrowser
import org.scalatest.{Assertions, Matchers}
import uk.gov.hmrc.softdrinksindustrylevyfrontend.generic.WebPage


trait NavigationSugar extends WebBrowser with Eventually with Assertions with Matchers with IntegrationPatience {

  val robot = new Robot()

  def goOn(page: WebPage)(implicit webDriver: WebDriver) = {
    goTo(page)
    on(page)
  }

  def on(page: WebPage)(implicit webDriver: WebDriver) = {
    val wait = new WebDriverWait(webDriver, 5)
    wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")))
    assert(page.isCurrentPage, s"Page was not loaded: ${page.currentUrl}")
  }

  def notOn(page: WebPage)(implicit webDriver: WebDriver) = {
    eventually {
      webDriver.findElement(By.tagName("body"))
    }
    assertResult(false, s"\nDid not expect ${page.currentUrl} to be loaded") {
      page.isCurrentPage
    }
  }

  def loadPage()(implicit webDriver: WebDriver) = {
    val wait = new WebDriverWait(webDriver, 15)
    wait.until(
      new ExpectedCondition[WebElement] {
        override def apply(d: WebDriver) = d.findElement(By.tagName("body"))
      }
    )
  }

  def anotherTabIsOpened()(implicit webDriver: WebDriver) = {
    webDriver.getWindowHandles.size() should be(2)
  }

  def browserGoBack()(implicit webDriver: WebDriver) = {
    webDriver.navigate().back()
  }

  def switchToTabWith(marker: => Boolean)(implicit webDriver: WebDriver): Any = {
    windowHandles.foreach { newTab: String =>
      switch to window(newTab)
      if (marker) return
    }
    fail(s"Marker evaluation resolves false for current page")
  }
}
