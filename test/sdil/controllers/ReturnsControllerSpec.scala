/*
 * Copyright 2019 HM Revenue & Customs
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

package sdil.controllers

import cats.implicits._
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import play.api.libs.json._
import play.api.mvc._
import uk.gov.hmrc.http.{CoreDelete, CoreGet, CorePut, HeaderCarrier}
import com.softwaremill.macwire._
import play.api.test.FakeRequest
import uk.gov.hmrc.http.cache.client.ShortLivedHttpCaching

import scala.concurrent._
import duration._
import org.scalatest.MustMatchers._
import com.softwaremill.macwire._
import org.mockito.ArgumentMatchers.{any, eq => matching}
import org.mockito.Mockito._
import play.api.i18n.Messages
import play.api.test.FakeRequest
import play.api.test.Helpers._
import sdil.config.SDILShortLivedCaching
import sdil.models._, backend._, retrieved._
import uk.gov.hmrc.auth.core.AffinityGroup.Organisation
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.~
import play.api.libs.json._
import scala.concurrent.Future

class ReturnsControllerSpec extends ControllerSpec {

  "ReturnsController" should {

    "execute main program" in {

      def subProgram = controller.program(
        ReturnPeriod(2018,1),
        RetrievedSubscription("", "", "", UkAddress(Nil,""), RetrievedActivity(false, false, false, false, false), java.time.LocalDate.now, Nil, Nil, Contact(None, None, "", "")),
        "",
        false,
        "start"
      )(hc)

      val output = controllerTester.testJourney(subProgram)(
        "claim-credits-for-exports"            -> Json.obj("lower" -> 6789, "higher" -> 2345),
        "packaged-as-a-contract-packer"        -> Json.obj("lower" -> 1234579, "higher" -> 2345679),
        "claim-credits-for-lost-damaged"       -> Json.obj("lower" -> 123, "higher" -> 234),
        "brought-into-uk-from-small-producers" -> Json.obj("lower" -> 1234, "higher" -> 2345),
        "_editSmallProducers"                  -> Json.toJson(false),
        "own-brands-packaged-at-own-sites"     -> Json.obj("lower" -> 123234, "higher" -> 2340000),
        "small-producer-details"               -> JsString("Done"),
        "return-change-registration"           -> JsNull,
        "brought-into-uk"                      -> Json.obj("lower" -> 1234562, "higher" -> 2345672),
        "ask-secondary-warehouses-in-return"   -> Json.toJson(false),
//        "exemptions-for-small-producers"       -> Json.toJson(false),
        "pack-at-business-address-in-return"   -> Json.toJson(false),
        "first-production-site" -> Json.obj("address" -> Json.obj("lines" -> List("117 Jerusalem Courtz","St Albans"),"postCode" -> "AL10 3UJ")),
        "production-site-details_data" -> JsArray(List(
          Json.obj("address" -> Json.obj("lines" -> List("117 Jerusalem Courtz","St Albans"),"postCode" -> "AL10 3UJ")),
          Json.obj("address" -> Json.obj("lines" -> List("12 The Street","Blahdy Corner"),"postCode" -> "AB12 3CD"))
        )) //,
//        "production-site-details" -> JsString("Done")
      )
      println(Await.result(output, 10 seconds))

      1 mustBe 1
    }

    // "askNewWarehouses" in {
    //   def subProgram = controller.askNewWarehouses()(hc) >>
    //   controller.resultToWebMonad[Result](controller.Ok("fin!"))

    //   // dummy up a post request, may need to add in a session id too in order to avoid a redirect
    //   def request: Request[AnyContent] = FakeRequest().withFormUrlEncodedBody("utr" -> "", "postcode" -> "").withSession { ("uuid" -> sessionUUID) }
    //   def output = controller.runInner(request)(subProgram)(
    //     "production-site-details" /* replace with the key of the very last form page */
    //   )(persistence.dataGet, persistence.dataPut)
    //   // We should get 'fin!' in the result as long as all the validation passes
    //   // println(Await.result(output, 10 seconds).getClass)
    //   // Await.result(output, 10 seconds).toString must contain("fin")
    //   println(Await.result(output, 10 seconds).body.toString)
    //   1 mustBe 1

    // }
  }

  lazy val controller: ReturnsController = wire[ReturnsController]
  lazy val controllerTester = new UniformControllerTester(controller)

  lazy val shortLivedCaching: ShortLivedHttpCaching = new ShortLivedHttpCaching {
    override def baseUri: String = ???
    override def domain: String = ???
    override def defaultSource: String = ???
    override def http: CoreGet with CorePut with CoreDelete = ???
  }
  lazy val hc: HeaderCarrier = HeaderCarrier()

  // DATA OUT:Map(claim-credits-for-exports -> {"lower":6789,"higher":2345}, packaged-as-a-contract-packer -> {"lower":1234579,"higher":2345679}, claim-credits-for-lost-damaged -> {"lower":123,"higher":234}, brought-into-uk-from-small-producers -> {"lower":1234,"higher":2345}, _editSmallProducers -> false, own-brands-packaged-at-own-sites -> {"lower":123234,"higher":2340000}, small-producer-details -> "Done", return-change-registration -> null, brought-into-uk -> {"lower":1234562,"higher":2345672}, ask-secondary-warehouses-in-return -> false, exemptions-for-small-producers -> false)


}
