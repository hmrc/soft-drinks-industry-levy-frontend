package uk.gov.hmrc.softdrinksindustrylevyfrontend.util

import net.lightbody.bmp.client.ClientUtil
import net.lightbody.bmp.proxy.CaptureType
import net.lightbody.bmp.{BrowserMobProxy, BrowserMobProxyServer}
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.remote.{CapabilityType, DesiredCapabilities}
import org.openqa.selenium.{Proxy, WebDriver}

object Env {

  // NOTE: The values of the 'environment' values below must be from the set defined in
  // Configuration.scala. This property is also read by Configuration.scala, which will
  // throw an exception if an unexpected value is defined.
  // See: https://github.tools.tax.service.gov.uk/HMRC/scala-webdriver/blob/master/src/main/scala/uk/gov/hmrc/integration/utils/Configuration.scala
  //
  val baseUrl = Option(System.getProperty("environment")) match {
    case Some("dev") => Urls.DEV
    case Some("qa") => Urls.PLATFORM_QA
    case Some("staging") => Urls.STAGING
    case Some("local") => Urls.LOCAL
    // Hijacking "smoke" so we can write tests against IBT env
    case Some("smoke") => Urls.PORTAL_QA
    case _ => Urls.LOCAL
  }

  val withHar = Option(System.getProperty("withHar")).isDefined

  //    val withHar = true

  lazy val driver: WebDriver = SingletonDriver.getInstance()

  val proxy: BrowserMobProxy = new BrowserMobProxyServer()
  lazy val driverWithProxy: WebDriver = {
    proxy.start()
    val seleniumProxy: Proxy = ClientUtil.createSeleniumProxy(proxy)
    val capabilities: DesiredCapabilities = DesiredCapabilities.chrome()
    capabilities.setCapability(CapabilityType.PROXY, seleniumProxy)
    val driver: WebDriver = new ChromeDriver(capabilities)
    proxy.enableHarCaptureTypes(CaptureType.REQUEST_CONTENT, CaptureType.REQUEST_HEADERS, CaptureType.RESPONSE_HEADERS)
    driver
  }

  def isQA(url: String): Boolean = Seq(Urls.PLATFORM_QA, Urls.PORTAL_QA).exists(url.startsWith(_))

  def isDev(url: String): Boolean = url.startsWith(Urls.DEV)

  def isLocal(url: String): Boolean = url.startsWith(Urls.LOCAL)

  def isStaging(url: String): Boolean = url.startsWith(Urls.STAGING)

  def isMDTP(url: String): Boolean = Seq(Urls.PLATFORM_QA, Urls.DEV, Urls.LOCAL, Urls.STAGING).exists(url.startsWith(_))

  def isPortal(url: String): Boolean = url.startsWith(Urls.PORTAL_QA)
}
