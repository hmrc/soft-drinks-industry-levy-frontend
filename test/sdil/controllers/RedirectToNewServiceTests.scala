/*
 * Copyright 2023 HM Revenue & Customs
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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import play.api.test.Helpers._
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status.SEE_OTHER
import play.api.i18n.MessagesApi
import play.api.inject.Injector
import play.api.mvc.{AnyContent, MessagesControllerComponents, Request}
import play.api.test.{FakeRequest, Injecting, StubMessagesFactory}
import sdil.actions.{AuthorisedAction, AuthorisedRequest}
import sdil.config.{AppConfig, RegistrationFormDataCache}
import sdil.connectors.SoftDrinksIndustryLevyConnector
import sdil.models.{Address, OrganisationDetails, RegistrationFormData, RosmRegistration}
import uk.gov.hmrc.auth.core.AffinityGroup.Organisation
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.auth.core.{AffinityGroup, AuthConnector, CredentialRole, Enrolments, User}
import views.Views
import views.softdrinksindustrylevy.errors.Errors

import scala.concurrent.{ExecutionContext, Future}

class RedirectToNewServiceTests extends AnyFreeSpec
  with GuiceOneAppPerSuite
  with Matchers
  with Injecting
  with StubMessagesFactory
  with ScalaFutures
  with IntegrationPatience
  with MockitoSugar {

  type Retrieval = Enrolments ~ Option[CredentialRole] ~ Option[String] ~ Option[AffinityGroup]


  lazy val injector: Injector = app.injector
  implicit val ec: ExecutionContext = injector.instanceOf[ExecutionContext]
  val views = injector.instanceOf[Views]
  val errors = injector.instanceOf[Errors]
  val messagesApi = injector.instanceOf[MessagesApi]
  lazy val mcc: MessagesControllerComponents =
    stubMessagesControllerComponents()
  val mockCache = mock[RegistrationFormDataCache]
  val mockAuthConnector = mock[AuthConnector]
  val mockSdilConnector = mock[SoftDrinksIndustryLevyConnector]
  val mockConfig = mock[AppConfig]

  val UTR = "1234567890"
  val INTERNALID = "abcdefghijkl"
  val REQUEST: Request[AnyContent] = FakeRequest()

  val authorisedRequest: AuthorisedRequest[AnyContent] = AuthorisedRequest(Some(UTR), INTERNALID, Enrolments(Set.empty), REQUEST)

  val authAction = new AuthorisedAction(mockAuthConnector, messagesApi, mockSdilConnector, mcc, errors)(mockConfig, ec)
  lazy val defaultRosmData: RosmRegistration = RosmRegistration(
    "some-safe-id",
    Some(
      OrganisationDetails(
        "an organisation"
      )),
    None,
    Address("1", "The Road", "", "", "AA11 1AA")
  )
  def stubAuthResult(res: Enrolments ~ Option[CredentialRole]) =
    when(mockAuthConnector.authorise[Retrieval](any(), any())(any(), any())).thenReturn {
      Future.successful(new~(new~(res, Some(INTERNALID)), Some(Organisation)))
    }
  val identifyController = new IdentifyController(mcc, mockCache, authAction, mockSdilConnector, views)(mockConfig, ec)

  "Identify.start" - {
    "when redirectToNewRegistrationsEnabled is true" - {
      "and the cache is empty" - {
        "should redirect to sdilNewRegistrationUrl" in {
          stubAuthResult(new ~(Enrolments(Set.empty), Some(User)))
          when(mockCache.get(any())(any())).thenReturn(Future.successful(None))
          when(mockConfig.redirectToNewRegistrationsEnabled).thenReturn(true)
          when(mockConfig.sdilNewRegistrationUrl).thenReturn("http://example.com")

          val res = identifyController.start.apply(REQUEST)

          status(res) mustBe SEE_OTHER
          redirectLocation(res) mustBe Some("http://example.com")
        }
      }

      "and the cache is not empty" - {
        "should redirect to verify and not to the new sdilRegistration" in {
          stubAuthResult(new~(Enrolments(Set.empty), Some(User)))
          when(mockCache.get(any())(any())).thenReturn(Future.successful(Some(RegistrationFormData(defaultRosmData, "1234567892"))))

          val res = identifyController.start.apply(REQUEST)

          status(res) mustBe SEE_OTHER
          redirectLocation(res) mustBe Some("http://example.com")
        }
      }
    }
  }
}
