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
import sdil.actions.AuthorisedRequest

class RegistrationControllerSpec extends ControllerSpec {

  lazy val controller: RegistrationController = wire[RegistrationController]
  lazy val controllerTester = new UniformControllerTester(controller)
  implicit val hc: HeaderCarrier = HeaderCarrier()

  def request = AuthorisedRequest[AnyContent](
    None, "", Enrolments(Set.empty), FakeRequest()
    .withFormUrlEncodedBody("utr" -> ""))


  "RegistrationController" should {
    "execute main program" in {
      def formData = RegistrationFormData(
        RosmRegistration("safeId", None, None,
          Address("","","","","")
        ),
        "utr"
      )
      val program = controller.program(formData)(request,hc)
      val output = controllerTester.testJourney(program)(
        "contact-details" -> Json.obj(
          "fullName" -> "Fred",
          "position" -> "Smith",
          "phoneNumber" -> "01234 567890",
          "email" -> "fred.smith@ecola.co.uk"
        ),
        "ask-secondary-warehouses" -> JsBoolean(false),
        "production-site-details" -> JsString("Done"),
        "start-date" -> JsString("2018-10-01"),
        "import" -> Json.obj("lower" -> 2345, "higher" -> 56789),
        "pack-at-business-address" -> JsBoolean(true),
        "package-own-uk" -> Json.obj("lower" -> 1000000, "higher" -> 2000000),
        "package-copack" -> Json.obj("lower" -> 110000, "higher" -> 130000),
        "organisation-type" -> JsString("limitedCompany"),
        "producer" -> JsString("Large")
      )

      println(Await.result(output, 10 seconds))

      1 mustBe 1

    }
  }

}

//  val lastPage = "declaration"
