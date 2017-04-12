package uk.gov.hmrc.softdrinksindustrylevyfrontend.acceptancetests.util

import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeDriver

trait ImplicitWebDriverSugar {
  implicit val webDriver: WebDriver = if (Env.withHar) Env.driverWithProxy else Env.driver
}