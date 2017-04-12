package uk.gov.hmrc.softdrinksindustrylevyfrontend.util

import org.openqa.selenium.{By, WebElement}
import org.scalatest.selenium.WebBrowser

import scala.util.Try

case class Link(href: String, text: String)

trait Section extends WebBrowser with ImplicitWebDriverSugar {

  def sectionQuery: Query

  def text = section.get.text

  def SectionNotDisplayedException = new NoSuchElementException("Section not displayed: " + sectionQuery)

  def section: Option[Element] = find(sectionQuery)

  def displayed = section.fold(false)(_.isDisplayed)

  override def toString: String = s"Section(${sectionQuery.toString})"

  def find(by: By): Option[WebElement] = section.flatMap(s => Try(s.underlying.findElement(by)).toOption)

  def elementToLink(element: Option[WebElement]): Option[Link] =
    element match {
      case Some(e) => Some(Link(e.getAttribute("href"), e.getText))
      case None => None
    }

  def findLink(id: String): Option[Link] = elementToLink(find(By.id(id)))

  def findLinkByText(text: String) = find(linkText(text)) match {
    case Some(e) => Some(Link(e.attribute("href").get, e.text))
    case None => None
  }

}
