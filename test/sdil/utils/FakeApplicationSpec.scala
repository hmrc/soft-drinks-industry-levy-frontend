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

package sdil.utils

import com.softwaremill.macwire.wire
import org.mockito.ArgumentMatchers.{any, anyString, eq => matching}
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.{BaseOneAppPerSuite, FakeApplicationFactory, PlaySpec}
import play.api.ApplicationLoader.Context
import play.api.i18n.{Lang, Messages, MessagesApi}
import play.api.inject.DefaultApplicationLifecycle
import play.api.mvc._
import play.api.test.Helpers
import play.api.{Application, Configuration, Environment}
import play.twirl.api.Html
import sdil.actions.{AuthorisedAction, FormAction, RegisteredAction}
import sdil.config._
import sdil.connectors.{DirectDebitBackendConnector, GaConnector, PayApiConnector, SoftDrinksIndustryLevyConnector}
import sdil.models.backend._
import sdil.models.retrieved.{RetrievedActivity, RetrievedSubscription}
import sdil.models.{ReturnPeriod, ReturnsFormData, ReturnsVariation, SdilReturn}
import uk.gov.hmrc.auth.core.AffinityGroup.Organisation
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.http.cache.client.{CacheMap, SessionCache, ShortLivedHttpCaching}
import uk.gov.hmrc.play.bootstrap.frontend.http.FrontendErrorHandler
import uk.gov.hmrc.play.config._
import uk.gov.hmrc.play.views.html.helpers._
import uk.gov.hmrc.play.views.html.layouts._
import views.Views
import views.html.softdrinksindustrylevy.errors.{already_registered, invalid_affinity, invalid_role, registration_pending}
import views.html.softdrinksindustrylevy.{balance_history, deregistered_service_page, service_page}
import views.html.{error_template, time_out}
import views.softdrinksindustrylevy.errors.Errors
import views.uniform.Uniform

import java.io.File
import java.time.LocalDate
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

trait FakeApplicationSpec extends PlaySpec with BaseOneAppPerSuite with FakeApplicationFactory with MockitoSugar {
  override def fakeApplication: Application = {
    val context = Context.create(
      environment = env,
      lifecycle = new DefaultApplicationLifecycle()
    )
    val loader = new SDILApplicationLoader
    loader.load(context)
  }

  // in the controller where you want to access messages override this lazy val
  // e.g. Map("en" -> Map("heading.partnerships" -> "someOtherValueShouldAppear"))
  lazy val stubMessages: Map[String, Map[String, String]] = Map.empty
  implicit val lang: Lang = Lang("en") // lang used for messages en == english
  implicit lazy val stubMessagesControllerComponents: MessagesControllerComponents = {
    def stub = Helpers.stubControllerComponents(messagesApi = Helpers.stubMessagesApi(messages = stubMessages))
    new DefaultMessagesControllerComponents(
      new DefaultMessagesActionBuilderImpl(Helpers.stubBodyParser(AnyContentAsEmpty), stub.messagesApi)(
        stub.executionContext),
      DefaultActionBuilder(stub.actionBuilder.parser)(stub.executionContext),
      stub.parsers,
      stub.messagesApi,
      stub.langs,
      stub.fileMimeTypes,
      stub.executionContext
    )
  }

  val messagesApi: MessagesApi = stubMessagesControllerComponents.messagesApi
  implicit val defaultMessages: Messages = messagesApi.preferred(Seq.empty)
  val returnPeriod = ReturnPeriod(2018, 1)
  val returnPeriods = List(ReturnPeriod(2018, 1), ReturnPeriod(2019, 1))
  val returnPeriods2 = List(ReturnPeriod(2018, 1), ReturnPeriod(2019, 1), ReturnPeriod(2019, 2), ReturnPeriod(2019, 3))
  val defaultSubscription: Subscription = {
    Subscription(
      "1234567890",
      "Patel's Sugary Syrup",
      "limited company",
      UkAddress(List("41", "my street", "my town", "my county"), "BN4 4GT"),
      Activity(None, None, None, None, true),
      LocalDate.of(2018, 4, 6),
      Nil,
      Nil,
      Contact(Some("Rooty Tooty"), Some("Head of Household"), "01517362873", "a@a.com")
    )
  }

  val mockCache: RegistrationFormDataCache = {
    val m = mock[RegistrationFormDataCache]
    when(m.cache(anyString(), any())(any())).thenReturn(Future.successful(CacheMap("", Map.empty)))
    when(m.get(anyString())(any())).thenReturn(Future.successful(None))
    when(m.clear(anyString())(any())).thenReturn(Future.successful(()))
    //when(m.shortLiveCache) thenReturn cacheMock
    m
  }

  val mockReturnsCache: ReturnsFormDataCache = {
    val m = mock[ReturnsFormDataCache]
    when(m.get(anyString())(any())).thenReturn(Future.successful(None))
    m
  }

  val mockRegVariationsCache: RegistrationVariationFormDataCache = {
    val m = mock[RegistrationVariationFormDataCache]
    when(m.get(anyString())(any())).thenReturn(Future.successful(None))
    when(m.clear(anyString())(any())).thenReturn(Future.successful(()))
    m
  }

  val mockRetVariationsCache: ReturnVariationFormDataCache = {
    val m = mock[ReturnVariationFormDataCache]
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
    when(m.fetchAndGetEntry[Any](any(), any())(any(), any(), any())).thenReturn(Future.successful(None))
    m
  }

  implicit lazy val testConfig: TestConfig = new TestConfig(configuration)

  lazy val mockErrorHandler = {
    val m = mock[FrontendErrorHandler]
    when(m.notFoundTemplate(any())).thenReturn(Html("not found"))
    m
  }

  lazy val env: Environment = Environment.simple(new File("."))
  lazy val configuration: Configuration = Configuration.load(env, Map("metrics.enabled" -> false.asInstanceOf[AnyRef]))

  val aSubscription = RetrievedSubscription(
    "0000000022",
    "XKSDIL000000022",
    "Super Lemonade Plc",
    UkAddress(List("63 Clifton Roundabout", "Worcester"), "WR53 7CX"),
    RetrievedActivity(false, true, false, false, false),
    LocalDate.of(2018, 4, 19),
    List(
      Site(
        UkAddress(List("33 Rhes Priordy", "East London"), "E73 2RP"),
        Some("88"),
        Some("Wild Lemonade Group"),
        Some(LocalDate.of(2018, 2, 26))),
      Site(
        UkAddress(List("117 Jerusalem Court", "St Albans"), "AL10 3UJ"),
        Some("87"),
        Some("Highly Addictive Drinks Plc"),
        Some(LocalDate.of(2019, 8, 19))),
      Site(
        UkAddress(List("87B North Liddle Street", "Guildford"), "GU34 7CM"),
        Some("94"),
        Some("Monster Bottle Ltd"),
        Some(LocalDate.of(2017, 9, 23))),
      Site(
        UkAddress(List("122 Dinsdale Crescent", "Romford"), "RM95 8FQ"),
        Some("27"),
        Some("Super Lemonade Group"),
        Some(LocalDate.of(2017, 4, 23))),
      Site(
        UkAddress(List("105B Godfrey Marchant Grove", "Guildford"), "GU14 8NL"),
        Some("96"),
        Some("Star Products Ltd"),
        Some(LocalDate.of(2017, 2, 11)))
    ),
    List(),
    Contact(Some("Ava Adams"), Some("Chief Infrastructure Agent"), "04495 206189", "Adeline.Greene@gmail.com"),
    None
  )

  lazy val mockreturnFormData = ReturnsFormData(sdilReturn, mockreturnVariation)

  lazy val mockreturnVariation = ReturnsVariation(
    "Super Lemonade Plc",
    UkAddress(List("63 Clifton Roundabout", "Worcester"), "WR53 7CX"),
    (false, (0, 0)),
    (false, (0, 0)),
    List(
      Site(
        UkAddress(List("33 Rhes Priordy", "East London"), "E73 2RP"),
        Some("88"),
        Some("Wild Lemonade Group"),
        Some(LocalDate.of(2018, 2, 26))),
      Site(
        UkAddress(List("117 Jerusalem Court", "St Albans"), "AL10 3UJ"),
        Some("87"),
        Some("Highly Addictive Drinks Plc"),
        Some(LocalDate.of(2019, 8, 19))),
      Site(
        UkAddress(List("87B North Liddle Street", "Guildford"), "GU34 7CM"),
        Some("94"),
        Some("Monster Bottle Ltd"),
        Some(LocalDate.of(2017, 9, 23))),
      Site(
        UkAddress(List("122 Dinsdale Crescent", "Romford"), "RM95 8FQ"),
        Some("27"),
        Some("Super Lemonade Group"),
        Some(LocalDate.of(2017, 4, 23))),
      Site(
        UkAddress(List("105B Godfrey Marchant Grove", "Guildford"), "GU14 8NL"),
        Some("96"),
        Some("Star Products Ltd"),
        Some(LocalDate.of(2017, 2, 11)))
    ),
    Nil,
    "0000000000",
    "Email@FakeEmail.com",
    10000
  )

  val sdilReturn = SdilReturn((0, 0), (0, 0), List.empty, (0, 0), (0, 0), (0, 0), (0, 0))

  lazy val cacheMock = mock[ShortLivedHttpCaching]

  lazy val mockSdilConnector: SoftDrinksIndustryLevyConnector = {
    val m = mock[SoftDrinksIndustryLevyConnector]
    when(m.submit(any(), any())(any())).thenReturn(Future.successful(()))
    when(m.retrieveSubscription(any(), any())(any())).thenReturn(Future.successful(None))
    when(m.retrieveSubscription(matching("XZSDIL000100107"), any())(any()))
      .thenReturn(Future.successful(Some(aSubscription)))
    when(m.returns_pending(any())(any())).thenReturn(Future.successful(Nil))
    when(m.returns_variable(any())(any())).thenReturn(Future.successful(returnPeriods))
    when(m.returns_vary(any(), any())(any())).thenReturn(Future.successful(()))
    when(m.returns_update(any(), any(), any())(any())).thenReturn(Future.successful(()))
    when(m.returns_get(any(), any())(any())).thenReturn(Future.successful(None))
    when(m.returns_variation(any(), any())(any())).thenReturn(Future.successful(()))
    when(m.submitVariation(any(), any())(any())).thenReturn(Future.successful(()))
    when(m.balanceHistory(any(), any())(any())).thenReturn(Future.successful(Nil))
    when(m.balance(any(), any())(any())).thenReturn(Future.successful(BigDecimal(0)))
    //when(m.shortLiveCache) thenReturn cacheMock
    when(cacheMock.fetchAndGetEntry[Any](any(), any())(any(), any(), any())).thenReturn(Future.successful(None))
    when(cacheMock.cache(anyString(), anyString(), any())(any(), any(), any()))
      .thenReturn(Future.successful(CacheMap("jakeAndIan", Map())))
    when(m.checkSmallProducerStatus(any(), any())(any())) thenReturn Future.successful(None)
    when(m.submit(any(), any())(any())) thenReturn Future.successful(())
    m
  }

  lazy val mockRegistrationFormDataCache = {}

  type Retrieval = Enrolments ~ Option[CredentialRole] ~ Option[String] ~ Option[AffinityGroup]

  lazy val mockAuthConnector: AuthConnector = {
    val m = mock[AuthConnector]
    when(m.authorise[Retrieval](any(), any())(any(), any())).thenReturn {
      Future.successful(new ~(new ~(new ~(Enrolments(Set.empty), Some(User)), Some("internal id")), Some(Organisation)))
    }
    m
  }

  lazy val mockPayApiConnector: PayApiConnector = mock[PayApiConnector]
  lazy val mockDirectDebitBackendConnector: DirectDebitBackendConnector = mock[DirectDebitBackendConnector]

  lazy val mockGaConnector: GaConnector = {
    val m = mock[GaConnector]
    when(m.sendEvent(any())(any(), any())).thenReturn(Future.successful(()))
    m
  }
  lazy val formAction: FormAction = wire[FormAction]
  lazy val authorisedAction: AuthorisedAction = wire[AuthorisedAction]
  lazy val registeredAction: RegisteredAction = wire[RegisteredAction]

  lazy val errors: Errors = wire[Errors]
  lazy val Views: Views = wire[Views]
  lazy val baseFoo = wire[views.html.uniform.base]
  lazy val uniformHelpers: Uniform = wire[Uniform]
//  lazy val viewHelpers: ViewHelpers = wire[ViewHelpers]
  lazy val govukTemplate: views.html.layouts.GovUkTemplate = wire[views.html.layouts.GovUkTemplate]

  lazy val alreadyRegistered: already_registered = wire[already_registered]
  lazy val invalidAffinity: invalid_affinity = wire[invalid_affinity]
  lazy val invalidRole: invalid_role = wire[invalid_role]
  lazy val registrationPending: registration_pending = wire[registration_pending]
  lazy val timeOut: time_out = wire[time_out]
  lazy val identify: views.html.softdrinksindustrylevy.register.identify =
    wire[views.html.softdrinksindustrylevy.register.identify]
  lazy val verifyView: views.html.softdrinksindustrylevy.register.verify =
    wire[views.html.softdrinksindustrylevy.register.verify]
  lazy val balanceHistory: balance_history = wire[balance_history]
  lazy val deregisteredServicePage: deregistered_service_page = wire[deregistered_service_page]
  lazy val servicePage: service_page = wire[service_page]
  lazy val errorTemplate: error_template = wire[error_template]

  lazy val updateBusinessAddresses: views.html.uniform.fragments.update_business_addresses =
    wire[views.html.uniform.fragments.update_business_addresses]
  lazy val end: views.html.uniform.end = wire[views.html.uniform.end]

  //copied from uk.gov.hmrc.play.views.html.helpers
  lazy val addressView: uk.gov.hmrc.play.views.html.helpers.Address = wire[uk.gov.hmrc.play.views.html.helpers.Address]
  lazy val dateFields: DateFields = wire[DateFields]
  lazy val dateFieldsFreeInline: DateFieldsFreeInline = wire[DateFieldsFreeInline]
  lazy val dateFieldsFreeInlineLegend: DateFieldsFreeInlineLegend = wire[DateFieldsFreeInlineLegend]
  lazy val dateFieldsFreeYearInline: DateFieldsFreeYearInline = wire[DateFieldsFreeYearInline]
  lazy val dateFieldsFreeYear: DateFieldsFreeYear = wire[DateFieldsFreeYear]
  lazy val dateFieldsInline: DateFieldsInline = wire[DateFieldsInline]
  lazy val dropdown: Dropdown = wire[Dropdown]
  lazy val errorInline: ErrorInline = wire[ErrorInline]
  lazy val errorNotifications: ErrorNotifications = wire[ErrorNotifications]
  lazy val errorSummary: ErrorSummary = wire[ErrorSummary]
  lazy val fieldGroup: FieldGroup = wire[FieldGroup]
  lazy val form: FormWithCSRF = wire[FormWithCSRF]
  lazy val input: Input = wire[Input]
  lazy val inputRadioGroup: InputRadioGroup = wire[InputRadioGroup]
  lazy val reportAProblemLink: ReportAProblemLink = wire[ReportAProblemLink]
  lazy val singleCheckbox: SingleCheckbox = wire[SingleCheckbox]
  lazy val textArea: TextArea = wire[TextArea]
  //copied from uk.gov.hmrc.play.views.html.layouts
  lazy val article: Article = wire[Article]
  lazy val attorneyBanner: AttorneyBanner = wire[AttorneyBanner]
  lazy val betaBanner: BetaBanner = wire[BetaBanner]
  lazy val footer: Footer = wire[Footer]
  lazy val euExitLinks: EuExitLinks = wire[EuExitLinks]
  lazy val footerLinks: FooterLinks = wire[FooterLinks]
  lazy val head: Head = wire[Head]
  lazy val headWithTrackingConsent: HeadWithTrackingConsent = wire[HeadWithTrackingConsent]
  lazy val trackingConsentSnippet: TrackingConsentSnippet = wire[TrackingConsentSnippet]
  lazy val trackingConsentConfig: TrackingConsentConfig = wire[TrackingConsentConfig]
  lazy val headerNav: HeaderNav = wire[HeaderNav]
  lazy val loginStatus: LoginStatus = wire[LoginStatus]
  lazy val mainContent: MainContent = wire[MainContent]
  lazy val mainContentHeader: MainContentHeader = wire[MainContentHeader]
  lazy val gtmSnippet: GTMSnippet = wire[GTMSnippet]
  lazy val serviceInfo: ServiceInfo = wire[ServiceInfo]
  lazy val sidebar: Sidebar = wire[Sidebar]

  lazy val optimizelyConfig: OptimizelyConfig = wire[OptimizelyConfig]
  lazy val assetConfig: AssetsConfig = wire[AssetsConfig]
  lazy val gtmConfig: GTMConfig = wire[GTMConfig]
  lazy val accessibilityStatementConfig: AccessibilityStatementConfig = wire[AccessibilityStatementConfig]
}
