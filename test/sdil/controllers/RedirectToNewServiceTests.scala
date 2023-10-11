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

import com.softwaremill.macwire.wire
import org.mockito.ArgumentMatchers.{any, anyString}
import org.mockito.Mockito.when
import org.mockito.stubbing.OngoingStubbing
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status.SEE_OTHER
import play.api.i18n.MessagesApi
import play.api.inject.Injector
import play.api.libs.json.JsValue
import play.api.mvc.{AnyContent, MessagesControllerComponents, Request}
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Injecting, StubMessagesFactory}
import sdil.actions.{AuthorisedAction, AuthorisedRequest, RegisteredAction}
import sdil.config.{AppConfig, RegistrationFormDataCache, ReturnsFormDataCache}
import sdil.connectors.SoftDrinksIndustryLevyConnector
import sdil.models.{Address, OrganisationDetails, RegistrationFormData, ReturnsFormData, RosmRegistration}
import sdil.uniform.SaveForLaterPersistenceNew
import uk.gov.hmrc.auth.core.AffinityGroup.Organisation
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.http.cache.client.ShortLivedHttpCaching
import views.Views
import views.softdrinksindustrylevy.errors.Errors
import views.uniform.Uniform

import scala.concurrent.{ExecutionContext, Future}

class RedirectToNewServiceTests
    extends AnyFreeSpec with GuiceOneAppPerSuite with Matchers with Injecting with StubMessagesFactory with ScalaFutures
    with IntegrationPatience with MockitoSugar {

  type Retrieval = Enrolments ~ Option[CredentialRole] ~ Option[String] ~ Option[AffinityGroup]

  lazy val injector: Injector = app.injector
  implicit val ec: ExecutionContext = injector.instanceOf[ExecutionContext]
  val views: Views = injector.instanceOf[Views]
  val errors: Errors = injector.instanceOf[Errors]
  val messagesApi: MessagesApi = injector.instanceOf[MessagesApi]
  lazy val mcc: MessagesControllerComponents =
    stubMessagesControllerComponents()
  val mockCache: RegistrationFormDataCache = mock[RegistrationFormDataCache]
  val mockAuthConnector: AuthConnector = mock[AuthConnector]
//  val mockPersistenceBase: SaveForLaterPersistenceNew[Request[AnyContent]] =
//    injector.instanceOf[SaveForLaterPersistenceNew[Request[AnyContent]]]
  val mockSdilConnector: SoftDrinksIndustryLevyConnector = mock[SoftDrinksIndustryLevyConnector]
  val mockConfig: AppConfig = mock[AppConfig]
  val mockReturnsCache: ReturnsFormDataCache = mock[ReturnsFormDataCache]
  val ufViews: Uniform = injector.instanceOf[Uniform]

  val INTERNALID = "abcdefghijkl"
  val REQUEST: Request[AnyContent] = FakeRequest()

  val authorisedRequest: AuthorisedRequest[AnyContent] =
    AuthorisedRequest(None, INTERNALID, Enrolments(Set.empty), REQUEST)

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

  val regAction = new RegisteredAction(mockAuthConnector, mockSdilConnector, mcc)(ec)
  def stubAuthResult(res: Enrolments ~ Option[CredentialRole]): OngoingStubbing[Future[Retrieval]] =
    when(mockAuthConnector.authorise[Retrieval](any(), any())(any(), any())).thenReturn {
      Future.successful(new ~(new ~(res, Some(INTERNALID)), Some(Organisation)))
    }
  val sdilEnrolment: EnrolmentIdentifier = EnrolmentIdentifier("EtmpRegistrationNumber", "XZSDIL000100107")
  def stubAuthResultEnrolled(res: Enrolments): OngoingStubbing[Future[Enrolments]] =
    when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any())).thenReturn {
      Future.successful(Enrolments(Set(Enrolment("HMRC-OBTDS-ORG", Seq(sdilEnrolment), "Active"))))
    }

  val identifyController = new IdentifyController(mcc, mockCache, authAction, mockSdilConnector, views)(mockConfig, ec)
  val returnsController =
    new ReturnsController(mcc, mockConfig, ufViews, regAction, mockSdilConnector, mockCache, mockReturnsCache)

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
          stubAuthResult(new ~(Enrolments(Set.empty), Some(User)))
          when(mockCache.get(any())(any()))
            .thenReturn(Future.successful(Some(RegistrationFormData(defaultRosmData, "1234567892"))))

          val res = identifyController.start.apply(REQUEST)

          status(res) mustBe SEE_OTHER
          redirectLocation(res) mustBe Some("/soft-drinks-industry-levy/register/verify")
        }
      }
    }
  }

  "Returns controller index " - {
    "when redirectToNewReturnsEnabled is true " - {
      "and the cache is empty " - {
        "should redirect to new returns frontend" in {
          stubAuthResultEnrolled(Enrolments(Set.empty))
          when(mockCache.get(any())(any())).thenReturn(Future.successful(None))
          when(mockConfig.redirectToNewReturnsEnabled).thenReturn(true)
          //when(mockPersistenceBase.dataGet()(any())).thenReturn(Future.successful(Map.empty[List[String], String]))
          when(mockConfig.startReturnUrl(2018, 1, isNilReturn = false)).thenReturn("http://example.com")
          when(mockReturnsCache.get(anyString())(any())).thenReturn(Future.successful(None))

          val res = returnsController.index(2023, 1, nilReturn = false, "idvalue").apply(REQUEST)

          status(res) mustBe SEE_OTHER
          redirectLocation(res) mustBe Some("http://example.com")
        }
      }

      "and the cache is not empty " - {
        "should stay on old service and start a return" in {
          stubAuthResultEnrolled(Enrolments(Set.empty))
          when(mockConfig.redirectToNewReturnsEnabled).thenReturn(true)
          when(mockCache.get(any())(any())).thenReturn(Future.successful(None))
          when(mockReturnsCache.get(anyString())(any()))
            .thenReturn(Future.successful(Some(ReturnsFormData(any(), any()))))
          //when(mockPersistenceBase.dataGet()(any())).thenReturn(Future.successful(any()))
          val res = returnsController.index(2023, 1, nilReturn = false, "idvalue").apply(REQUEST)

          status(res) mustBe SEE_OTHER
          redirectLocation(res) mustBe Some("/soft-drinks-industry-levy/own-brands-packaged-at-own-sites")
        }
      }
    }
  }

}
