/*
 * Copyright 2024 HM Revenue & Customs
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

package sdil.actions

import org.mockito.ArgumentMatchers.{eq => matching, _}
import org.mockito.Mockito._
import play.api.i18n.Messages
import play.api.inject.Injector
import play.api.mvc.Results._
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Results}
import play.api.test.{FakeRequest, StubMessagesFactory}
import play.api.test.Helpers._
import sdil.config.AppConfig
import sdil.controllers.ControllerSpec
import sdil.models.backend.{Contact, Site, UkAddress}
import sdil.models.retrieved.{RetrievedActivity, RetrievedSubscription}
import uk.gov.hmrc.auth.core.AffinityGroup.{Agent, Organisation}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.~

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

class ActionsWithNewServiceEnabledSpec extends ControllerSpec with StubMessagesFactory {

  val mockConfig = mock[AppConfig]
  lazy val injector: Injector = app.injector
  implicit val ec: ExecutionContext = injector.instanceOf[ExecutionContext]

  class HarnessAuth(authAction: AuthorisedAction) {
    def onPageLoad() = authAction { _ =>
      Results.Ok
    }
  }

  class HarnessRegistered(regAction: RegisteredAction) {
    def onPageLoad() = regAction { _ =>
      Results.Ok
    }
  }

  class HarnessForm(formAction: RegisteredAction) {
    def onPageLoad() = formAction { _ =>
      Results.Ok
    }
  }

  "AuthorisedAction" should {
    "redirect to the new sdilHome" when {
      "the newSdilService is enabled" in {
        val action: AuthorisedAction = new AuthorisedAction(
          mockAuthConnector,
          messagesApi,
          mockSdilConnector,
          stubMessagesControllerComponents,
          errors)(mockConfig, ec)

        val controller = new HarnessAuth(action)
        when(mockConfig.redirectToNewServiceEnabled) thenReturn true
        when(mockConfig.sdilNewHomeUrl) thenReturn "http://newSdilHome.com"
        val result = controller.onPageLoad()(FakeRequest())

        status(result) mustBe SEE_OTHER
        redirectLocation(result).value mustBe "http://newSdilHome.com"
      }
    }
  }

  "RegisteredAction" should {
    "redirect to the new sdilHome" when {
      "the newSdilService is enabled" in {
        val action: RegisteredAction =
          new RegisteredAction(mockAuthConnector, mockSdilConnector, mockConfig, stubMessagesControllerComponents)(ec)

        val controller = new HarnessRegistered(action)
        when(mockConfig.redirectToNewServiceEnabled) thenReturn true
        when(mockConfig.sdilNewHomeUrl) thenReturn "http://newSdilHome.com"
        val result = controller.onPageLoad()(FakeRequest())

        status(result) mustBe SEE_OTHER
        redirectLocation(result).value mustBe "http://newSdilHome.com"
      }
    }
  }
}
