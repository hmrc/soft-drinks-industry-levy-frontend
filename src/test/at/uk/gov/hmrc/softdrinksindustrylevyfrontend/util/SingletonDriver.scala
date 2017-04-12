
package uk.gov.hmrc.softdrinksindustrylevyfrontend.util

import java.io.{FileNotFoundException, IOException}
import java.net.{InetSocketAddress, URL}
import java.util.Properties
import java.util.logging.Level

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import net.lightbody.bmp.client.ClientUtil
import net.lightbody.bmp.proxy.auth.AuthType
import net.lightbody.bmp.{BrowserMobProxy, BrowserMobProxyServer}
import org.openqa.selenium.Proxy.ProxyType
import org.openqa.selenium.chrome.{ChromeDriver, ChromeOptions}
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.ie.InternetExplorerDriver
import org.openqa.selenium.logging.{LogType, LoggingPreferences}
import org.openqa.selenium.remote.{CapabilityType, DesiredCapabilities, RemoteWebDriver}
import org.openqa.selenium.{Proxy, WebDriver}

import scala.collection.JavaConversions._
import scala.io.Source

object SingletonDriver extends Driver

class Driver {
  private val SAUCY = "saucy"
  private val ZAP = "zap"
  var sessionID = ""

  // set flag to true to see full information about browserstack webdrivers on initialisation
  private val DRIVER_INFO_FLAG = true

  var instance: WebDriver = null
  private var baseWindowHandle: String = null
  var javascriptEnabled: Boolean = true
  val proxy: BrowserMobProxy = new BrowserMobProxyServer()

  def setJavascript(enabled: Boolean) {
    javascriptEnabled = enabled
    if (instance != null) closeInstance()
  }

  def getInstance(): WebDriver = {
    if (instance == null) {
      initialiseBrowser()
    }
    instance
  }

  def initialiseBrowser() {
    instance = createBrowser()
    instance.manage().window().maximize()
    //    instance.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS) //polls for 10 seconds before timeout
    baseWindowHandle = instance.getWindowHandle
  }

  def closeInstance() = {
    if (instance != null) {

      closeNewlyOpenedWindows()

      instance.close()
      instance = null
      baseWindowHandle = null
    }
  }

  def closeNewlyOpenedWindows() {
    instance.getWindowHandles.toList.foreach(w =>
      if (w != baseWindowHandle) instance.switchTo().window(w).close()
    )

    instance.switchTo().window(baseWindowHandle)
  }

  private def createBrowser(): WebDriver = {

    val logs = new LoggingPreferences()
    logs.enable(LogType.BROWSER, Level.SEVERE)

    def createFirefoxDriver: WebDriver = {
      val capabilities = DesiredCapabilities.firefox()
      addQaProxy(capabilities)
      capabilities.setCapability(CapabilityType.LOGGING_PREFS, logs)
      capabilities.setJavascriptEnabled(javascriptEnabled)
      capabilities.setCapability(CapabilityType.ACCEPT_SSL_CERTS, true)
      new FirefoxDriver(capabilities)
    }

    def createIEDriver: WebDriver = {
     val capabilities = DesiredCapabilities.internetExplorer()
      addQaProxy(capabilities)
      capabilities.setCapability(InternetExplorerDriver.INITIAL_BROWSER_URL, "https://www.google.co.uk")
      new InternetExplorerDriver(capabilities)
    }

    def createChromeDriver: WebDriver = {
      val options = new ChromeOptions()
      val capabilities = DesiredCapabilities.chrome()
      addQaProxy(capabilities)
      options.addArguments("test-type")
      capabilities.setCapability(ChromeOptions.CAPABILITY, options)
      val driver = new ChromeDriver(capabilities)
      driver.manage().window().maximize()
      val caps = driver.getCapabilities
      val browserName = caps.getBrowserName
      val browserVersion = caps.getVersion
      println(s"Browser name: $browserName, Version: $browserVersion")
      driver
    }

    //    def createPhantomJsDriver: WebDriver = {
    //      val cap = new DesiredCapabilities()
    //      cap.setJavascriptEnabled(javascriptEnabled)
    //      new PhantomJSDriver(cap)
    //    }

    def createSaucyDriver: WebDriver = {
      val capabilities = DesiredCapabilities.firefox()
      capabilities.setCapability(CapabilityType.LOGGING_PREFS, logs)
      capabilities.setCapability("version", "22")
      capabilities.setCapability("platform", "OS X 10.9")
      capabilities.setCapability("name", "Frontend Integration") // TODO: should we add a timestamp here?

      new RemoteWebDriver(
        new URL("http://Optimus:3e4f3978-2b40-4965-a6b3-4fb7243bc1f2@ondemand.saucelabs.com:80/wd/hub"), //
        capabilities)
    }

    def createBrowserStackDriver: WebDriver = {
      var userName: String = null
      var automateKey: String = null

      try {
        val prop: Properties = new Properties()
        prop.load(this.getClass().getResourceAsStream("/browserConfig.properties"))

        userName = prop.getProperty("username")
        automateKey = prop.getProperty("automatekey")
      }
      catch {
        case e: FileNotFoundException => e.printStackTrace();
        case e: IOException => e.printStackTrace();
      }

      // create capabilities with device/browser settings from config file
      val bsCaps = getBrowserStackCapabilities
      val desCaps = new DesiredCapabilities(bsCaps)

      // set additional generic capabilities
      desCaps.setCapability("browserstack.debug", "true")
      desCaps.setCapability("browserstack.local", "true")
      desCaps.setCapability("project", "SSTTP")
      desCaps.setCapability("build", "Acceptance Tests FE Build 0.31")

      val bsUrl = s"http://$userName:$automateKey@hub-cloud.browserstack.com/wd/hub"
      val rwd = new RemoteWebDriver(new URL(bsUrl), desCaps)
      val sessionId = rwd.getSessionId.toString
      sessionID = sessionId
      printCapabilities(rwd, DRIVER_INFO_FLAG)
      rwd
    }

    def getBrowserStackCapabilities: Map[String, Object] = {
      val testDevice = System.getProperty("testDevice", "BS_Win8_1_IE_11")
      val resourceUrl = s"/browserstackdata/$testDevice.json"
      val cfgJsonString = Source.fromURL(getClass.getResource(resourceUrl)).mkString

      val mapper = new ObjectMapper() with ScalaObjectMapper
      mapper.registerModule(DefaultScalaModule)
      mapper.readValue[Map[String, Object]](cfgJsonString)
    }

    def printCapabilities(rwd: RemoteWebDriver, fullDump: Boolean): Unit = {
      var key = ""
      var value: Any = null

      println("RemoteWebDriver Basic Capabilities >>>>>>")
      // step 1, print out the common caps which have getters
      val caps = rwd.getCapabilities
      val platform = caps.getPlatform
      println(s"platform : $platform")
      val browserName = caps.getBrowserName
      println(s"browserName : $browserName")
      val version = caps.getVersion
      println(s"version : $version")

      // step 2, print out common caps which need to be explicitly retrieved using their key
      val capsMap = caps.asMap()
      val basicKeyList = List("os", "os_version", "mobile", "device", "deviceName")
      for (key <- basicKeyList) {
        if (capsMap.containsKey(key)) {
          value = capsMap.get(key)
          println(s"$key : $value")
        } else {
          println(s"$key : not set")

        }
      }

      if (fullDump) {
        // step 3, if requested, dump everything
        println("Full Details >>>>>>")
        for (key <- capsMap.keySet()) {
          value = capsMap.get(key)
          println(s"$key : $value")
        }
      }
    }

    def createZapDriver: WebDriver = {
      val proxy: Proxy = new Proxy()
      proxy.setAutodetect(false)
      proxy.setProxyType(ProxyType.MANUAL)
      proxy.setHttpProxy("localhost:8080")

      val capabilities = DesiredCapabilities.firefox()

      capabilities.setCapability(CapabilityType.LOGGING_PREFS, logs)
      capabilities.setCapability(CapabilityType.PROXY, proxy)

      new FirefoxDriver(capabilities)
    }

    val environmentProperty = System.getProperty("browser", "firefox")
    environmentProperty match {
      case "firefox" => createFirefoxDriver
      case "browserstack" => createBrowserStackDriver
      case "chrome" => createChromeDriver
      case "ie" => createIEDriver
      case SAUCY => createSaucyDriver
      case ZAP => createZapDriver
      case _ => throw new IllegalArgumentException(s"Browser type not recognised: -D$environmentProperty")
    }
  }

  def addQaProxy(capabilities: DesiredCapabilities): Unit = {
    if (Option(System.getProperty("qa.proxy")).isDefined) {
      val proxySettingPattern = """(.+):(.+)@(.+):(\d+)""".r
      System.getProperty("qa.proxy") match {
        case proxySettingPattern(user, password, host, port) =>
          proxy.chainedProxyAuthorization(user, password, AuthType.BASIC)
          proxy.setChainedProxy(new InetSocketAddress(host, port.toInt))
        case _ => throw new RuntimeException("QA Proxy settings must be provided as username:password@proxyHost:proxyPortNumber")
      }
      proxy.setTrustAllServers(true)
      proxy.start()
      val seleniumProxy: Proxy = ClientUtil.createSeleniumProxy(proxy)
      seleniumProxy.setNoProxy("*www-qa*")
      capabilities.setCapability(CapabilityType.PROXY, seleniumProxy)
    }
  }

}