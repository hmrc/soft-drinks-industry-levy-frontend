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

package sdil.utils

import java.io.File
import java.time.LocalDate

import com.softwaremill.macwire.wire
import org.mockito.ArgumentMatchers.{any, anyString}
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.{BaseOneAppPerSuite, FakeApplicationFactory, PlaySpec}
import play.api.i18n.{DefaultMessagesApi, Lang, Messages, MessagesApi}
import play.api.inject.DefaultApplicationLifecycle
import play.api.libs.concurrent.Execution.defaultContext
import play.api.mvc._
import play.api.test.{FakeRequest, Helpers}
import play.core.DefaultWebCommands
import play.twirl.api.Html
import sdil.actions.{AuthorisedAction, FormAction, RegisteredAction}
import sdil.config.{RegistrationFormDataCache, SDILApplicationLoader}
import sdil.connectors.{GaConnector, SoftDrinksIndustryLevyConnector}
import sdil.controllers.SdilWMController
import sdil.models.{ReturnPeriod, SdilReturn}
import sdil.models.backend._
import sdil.models.retrieved.{RetrievedActivity, RetrievedSubscription}
import uk.gov.hmrc.auth.core.AffinityGroup.Organisation
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.http.cache.client.{CacheMap, SessionCache, ShortLivedHttpCaching}
import uk.gov.hmrc.play.bootstrap.http.FrontendErrorHandler

import scala.concurrent.{ExecutionContext, Future}
import java.io.File
import java.time.LocalDate

import com.softwaremill.macwire._
import org.mockito.ArgumentMatchers.{any, anyString, eq => matching}
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import play.api.i18n._
import play.api.libs.concurrent.Execution.defaultContext
import play.api.test.FakeRequest
import play.api.{Application, ApplicationLoader, Configuration, Environment}
import play.twirl.api.Html
import sdil.actions.{AuthorisedAction, FormAction, RegisteredAction}
import sdil.config.RegistrationFormDataCache
import sdil.connectors.{GaConnector, SoftDrinksIndustryLevyConnector}
import sdil.controllers.{ReturnsController, SdilWMController}
import sdil.models.{ReturnPeriod, SdilReturn}
import sdil.models.backend._
import sdil.models.retrieved.{RetrievedActivity, RetrievedSubscription}
import uk.gov.hmrc.auth.core.AffinityGroup.Organisation
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.http.cache.client.{CacheMap, SessionCache, ShortLivedHttpCaching}
import uk.gov.hmrc.play.bootstrap.http.FrontendErrorHandler
import uk.gov.hmrc.uniform.webmonad
import uk.gov.hmrc.uniform.webmonad.WebMonad
import cats.syntax._
import org.mockito.stubbing.OngoingStubbing

import scala.concurrent.{ExecutionContext, Future}

trait FakeApplicationSpec extends PlaySpec with BaseOneAppPerSuite with FakeApplicationFactory with MockitoSugar { // TestWiring {
  override def fakeApplication: Application = {
    val context = ApplicationLoader.Context(
      environment = env,
      sourceMapper = None,
      webCommands = new DefaultWebCommands,
      initialConfiguration = configuration,
      lifecycle = new DefaultApplicationLifecycle()
    )
    //    implicit lazy val mcc: MessagesControllerComponents = app.injector.instanceOf[MessagesControllerComponents]


    val loader = new SDILApplicationLoader
    loader.load(context)
  }

  val stubMessages: Map[String, Map[String, String]] = Map("en" -> Map("heading.partnerships" -> "someOtherValueShouldAppear"))
  implicit lazy val stubMessagesControllerComponents: MessagesControllerComponents = {
    def stub = Helpers.stubControllerComponents(messagesApi = Helpers.stubMessagesApi(messages = stubMessages))
    new DefaultMessagesControllerComponents(
      new DefaultMessagesActionBuilderImpl(Helpers.stubBodyParser(AnyContentAsEmpty), stub.messagesApi)(stub.executionContext),
      DefaultActionBuilder(stub.actionBuilder.parser)(stub.executionContext), stub.parsers,
      stub.messagesApi, stub.langs, stub.fileMimeTypes, stub.executionContext
    )
  }

//  new DefaultMessagesApiProvider(env, configuration, langs, httpConfiguration).get
    //app.injector.instanceOf[DefaultMessagesControllerComponents]

    implicit val lang:Lang = Lang("en")
    //  lazy val mockCc =  Helpers.stubControllerComponents() //StubMessagesControl // mock[MessagesControllerComponents]

    //  DefaultMessagesActionBuilderImpl

    //  val mcc =  DefaultMessagesControllerComponents(mockCc.actionBuilder, mockCc.parsers, mockCc.messagesApi, mockCc.langs, mockCc.fileMimeTypes)
    val returnPeriods = List(ReturnPeriod(2018,1), ReturnPeriod(2019, 1))
    val returnPeriods2 = List(ReturnPeriod(2018,1), ReturnPeriod(2019, 1), ReturnPeriod(2019, 2), ReturnPeriod(2019, 3))
    val defaultSubscription: Subscription = {
      Subscription(
        "1234567890",
        "Patel's Sugary Syrup",
        "limited company",
        UkAddress(List("41", "my street", "my town", "my county"), "BN4 4GT"),
        Activity(None, None, None, None, true),
        LocalDate.of(2018,4,6),
        Nil,
        Nil,
        Contact(Some("Rooty Tooty"), Some("Head of Household"), "01517362873", "a@a.com"))
    }

    val mockCache: RegistrationFormDataCache = {
      val m = mock[RegistrationFormDataCache]
      when(m.cache(anyString(), any())(any())).thenReturn(Future.successful(CacheMap("", Map.empty)))
      when(m.get(anyString())(any())).thenReturn(Future.successful(None))
      when(m.clear(anyString())(any())).thenReturn(Future.successful(()))
      m
    }

    val mockKeystore: SessionCache = {
      val m = mock[SessionCache]
      when(m.cache(anyString(), any())(any(), any(), any())).thenReturn(Future.successful(CacheMap("", Map.empty)))
      when(m.fetchAndGetEntry[Any](anyString())(any(), any(), any())).thenReturn(Future.successful(None))
      m
    }

    lazy val mockShortLivedCache: ShortLivedHttpCaching = {
      val m = mock[ShortLivedHttpCaching]
      when(m.fetchAndGetEntry[Any](any(),any())(any(),any(),any())).thenReturn(Future.successful(None))
      m
    }

    implicit lazy val testConfig: TestConfig = new TestConfig

    lazy val mockErrorHandler = {
      val m = mock[FrontendErrorHandler]
      when(m.notFoundTemplate(any())).thenReturn(Html("not found"))
      m
    }

    lazy val env: Environment = Environment.simple(new File("."))
    //scala will not compile implicit conversion from Boolean → AnyRef
    lazy val configuration: Configuration = Configuration.load(env, Map("metrics.enabled" -> false.asInstanceOf[AnyRef]))




    val messagesApi: MessagesApi = stubMessagesControllerComponents.messagesApi //
//   new DefaultMessagesApi()
//    implicit val defaultMessages: Messages = messagesApi.preferred(FakeRequest())
    implicit val defaultMessages: Messages = messagesApi.preferred(Seq.empty)
    implicit val ec: ExecutionContext = defaultContext

    // val returnsMock = mock[mockSdilConnector.returns.type]
    //when(mockSdilConnector.returns).thenReturn(returnsMock)
    val aSubscription =  RetrievedSubscription(
      "0000000022",
      "XKSDIL000000022",
      "Super Lemonade Plc",
      UkAddress(List("63 Clifton Roundabout", "Worcester"),"WR53 7CX"),RetrievedActivity(false,true,false,false,false),LocalDate.of(2018,4,19),List(Site(UkAddress(List("33 Rhes Priordy", "East London"),"E73 2RP"),Some("88"),Some("Wild Lemonade Group"),Some(LocalDate.of(2018,2,26))), Site(UkAddress(List("117 Jerusalem Court", "St Albans"),"AL10 3UJ"),Some("87"),Some("Highly Addictive Drinks Plc"),Some(LocalDate.of(2019,8,19))), Site(UkAddress(List("87B North Liddle Street", "Guildford"),"GU34 7CM"),Some("94"),Some("Monster Bottle Ltd"),Some(LocalDate.of(2017,9,23))), Site(UkAddress(List("122 Dinsdale Crescent", "Romford"),"RM95 8FQ"),Some("27"),Some("Super Lemonade Group"),Some(LocalDate.of(2017,4,23))), Site(UkAddress(List("105B Godfrey Marchant Grove", "Guildford"),"GU14 8NL"),Some("96"),Some("Star Products Ltd"),Some(LocalDate.of(2017,2,11)))),List(),Contact(Some("Ava Adams"),Some("Chief Infrastructure Agent"),"04495 206189","Adeline.Greene@gmail.com"),None)

    val sdilReturn = SdilReturn((0,0), (0,0), List.empty, (0,0), (0,0), (0,0), (0,0))

    lazy val cacheMock = mock[ShortLivedHttpCaching]

    lazy val mockSdilConnector: SoftDrinksIndustryLevyConnector = {
      val m = mock[SoftDrinksIndustryLevyConnector]
      when(m.submit(any(),any())(any())).thenReturn(Future.successful(()))
      when(m.retrieveSubscription(any(),any())(any())).thenReturn(Future.successful(None))
      when(m.retrieveSubscription(matching("XZSDIL000100107"),any())(any())).thenReturn(Future.successful(Some(aSubscription)))
      when(m.returns_pending(any())(any())).thenReturn(Future.successful(Nil))
      when(m.returns_variable(any())(any())).thenReturn(Future.successful(returnPeriods))
      when(m.returns_vary(any(), any())(any())).thenReturn(Future.successful(()))
      when(m.returns_update(any(), any(), any())(any())).thenReturn(Future.successful(()))
      when(m.returns_get(any(),any())(any())).thenReturn(Future.successful(None))
      when(m.returns_variation(any(),any())(any())).thenReturn(Future.successful(()))
      when(m.submitVariation(any(),any())(any())).thenReturn(Future.successful(()))
      when(m.balanceHistory(any(),any())(any())).thenReturn(Future.successful(Nil))
      when(m.balance(any(),any())(any())).thenReturn(Future.successful(BigDecimal(0)))
      when(m.shortLiveCache) thenReturn cacheMock
      when(cacheMock.fetchAndGetEntry[Any](any(),any())(any(),any(),any())).thenReturn(Future.successful(None))
      when(m.checkSmallProducerStatus(any(), any())(any())) thenReturn Future.successful(None)
      when(m.submit(any(), any())(any())) thenReturn Future.successful(())
      m
    }

    lazy val mockRegistrationFormDataCache = {

    }

    //  lazy val mockSubscription: Subscription.type = {
    //    val m = mock[Subscription]
    //    when(m.desify(defaultSubscription)) thenReturn defaultSubscription
    //    m
    //  }
    //  lazy val mockCachedFuture: OngoingStubbing[Future[Nothing] => WebMonad[Nothing]] = {
    //    val m = mock[webmonad.type]
    //    when(m.cachedFuture(any())) thenReturn WebMonad[]
    //  }
    lazy val mockSdilWMController: SdilWMController = {
      val m = mock[SdilWMController]
      when(m.isSmallProducer(any(), any(), any())(any())) thenReturn Future.successful(false)
      m
    }

    type Retrieval = Enrolments ~ Option[CredentialRole] ~ Option[String] ~ Option[AffinityGroup]

    lazy val mockAuthConnector: AuthConnector = {
      val m = mock[AuthConnector]
      when(m.authorise[Retrieval](any(), any())(any(), any())).thenReturn {
        Future.successful(new ~(new ~(new ~(Enrolments(Set.empty), Some(Admin)), Some("internal id")), Some(Organisation)))
      }
      m
    }

    lazy val mockGaConnector: GaConnector = {
      val m = mock[GaConnector]
      when(m.sendEvent(any())(any(), any())).thenReturn(Future.successful(()))
      m
    }
    lazy val formAction: FormAction = wire[FormAction]
    lazy val authorisedAction: AuthorisedAction = wire[AuthorisedAction]
    lazy val registeredAction: RegisteredAction = wire[RegisteredAction]
}
