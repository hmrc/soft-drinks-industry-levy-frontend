package uk.gov.hmrc.softdrinksindustrylevyfrontend

import java.io.IOException
import java.net.URI
import java.util

import net.lightbody.bmp.core.har.{HarEntry, HarRequest}
import org.apache.http.NameValuePair
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.HttpPut
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.message.BasicNameValuePair
import org.scalatest._
import play.api.libs.json.{JsArray, JsObject, JsString}
import uk.gov.hmrc.integration.framework.CookieManagement
import uk.gov.hmrc.softdrinksindustrylevyfrontend.generic.NginxFailurePage
import uk.gov.hmrc.softdrinksindustrylevyfrontend.util.{Env, ImplicitWebDriverSugar, NavigationSugar, SingletonDriver}

trait BaseFeatureSpec
  extends FeatureSpec
    with GivenWhenThen
    with Matchers
    with CookieManagement
    with BeforeAndAfterAllConfigMap
    with BeforeAndAfterEachTestData
    with ImplicitWebDriverSugar
    with NavigationSugar
    with Retries {

  override protected def beforeEach(testData: TestData): Unit = {
    Env.proxy.newHar(testData.name)
  }

  val fileExtensionBlacklist = Seq("js", "css", "png", "gif", "htm", "ico")

  override protected def afterEach(testData: TestData): Unit = {
    if (!Env.withHar) return
    println(JsObject(Seq("scenarioRequests" -> JsArray(Env.proxy.getHar.getLog.getEntries
      .filter(_.getRequest.getHeaders.exists(_.getValue.contains("text/html")))
      .filter(_.getRequest.getUrl.contains(Env.baseUrl))
      .filterNot(harEntry => {
        val url: String = harEntry.getRequest.getUrl
        fileExtensionBlacklist.contains(url.substring(url.lastIndexOf(".") + 1))
      })
      .filterNot(_.getRequest.getUrl.contains("gg/sign-in"))
      .map { entry: HarEntry =>
        val request: HarRequest = entry.getRequest
        val url: String = request.getUrl
        val parsedUrl = if (url.contains("return")) {
          s"/return${url.split("return")(1)}"
        }
        else {
          url.substring(Env.baseUrl.length)
        }
        JsObject(Seq(
          "method" -> JsString(request.getMethod),
          "elapsed" -> JsString(entry.getTime.toString),
          "url" -> JsString(parsedUrl),
          "headers" -> JsObject(request.getHeaders
            .filterNot(_.getName.contains("Cookie"))
            .map(header => header.getName -> JsString(header.getValue)))
        ).++(if (request.getMethod == "POST") {
          Seq("body" -> JsObject(request.getPostData.getParams
            .filterNot(_.getName.contains("version"))
            .map(param => param.getName -> JsString(param.getValue))))
        } else Seq.empty))
      }))))
  }

  override protected def afterAll(configMap: ConfigMap): Unit = {
    if (System.getProperty("browser") == "browserstack") webDriver.quit()
  }

  private def takeScreenShot(testMethodName: String) {
    println(s"******************* $testMethodName - FAILED *******************")
    println(s"Taking screenshot of '$testMethodName'")
    setCaptureDir("target/screenshots")
    try {
      captureTo(testMethodName)
    } catch {
      case e: IOException => e.printStackTrace()
    }
  }

  private def markBrowserstackTestAsFailed(test: String) = {
    println("****** ********Browserstack: Marking test as failed! *******************")
    val sessionID = SingletonDriver.sessionID
    println(sessionID)
    val uri: URI = new URI(s"https://stephenkam1:Rjj2RGvR7zqeDKc9AtTd@www.browserstack.com/automate/sessions/$sessionID.json")
    println(uri)
    val putRequest: HttpPut = new HttpPut(uri)

    val nameValuePairs: java.util.ArrayList[NameValuePair] = new java.util.ArrayList[NameValuePair]()
    nameValuePairs.add(new BasicNameValuePair("status", "failed"))
    nameValuePairs.add(new BasicNameValuePair("reason", test))
    putRequest.setEntity(new UrlEncodedFormEntity(nameValuePairs))

    HttpClientBuilder.create().build().execute(putRequest)
  }

  override def withFixture(test: NoArgTest) = {
    super.withFixture(test) match {
      case f: Failed => {
        takeScreenShot(test.name)
        if (System.getProperty("browser") == "browserstack") markBrowserstackTestAsFailed(test.name)
        if (NginxFailurePage.isCurrentPage) {
          println(s"Retrying ${test.name} after nginx error page shown")
          withRetry {
            super.withFixture(test)
          }
        } else f
      }
      case otherOutcome => {
        otherOutcome
      }
    }
  }

  def isSystemError: Boolean = {
    find(cssSelector("h1")).get.text == "System error"
  }

  def hasValidationErrors: Boolean = {
    findAll(cssSelector("div[class=field] > div[class=error]")).exists { error: Element => error.isDisplayed }
  }

  def checkPageTitle(title: String) = {
    pageTitle shouldBe title
    pageTitle.length should be < 66
    pageTitle should not include "."
    pageTitle should not include "/"
  }
}
