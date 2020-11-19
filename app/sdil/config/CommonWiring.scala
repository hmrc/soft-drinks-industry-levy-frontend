/*
 * Copyright 2020 HM Revenue & Customs
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

package sdil.config

import akka.stream.Materializer
import com.softwaremill.macwire.wire
import play.api.i18n.MessagesApi
import play.api.{Configuration, Environment, Mode}
import uk.gov.hmrc.http.cache.client.ShortLivedHttpCaching
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.config._
import uk.gov.hmrc.play.views.html.helpers._
import uk.gov.hmrc.play.views.html.layouts._
import views.{ViewHelpers, Views}
import views.html.softdrinksindustrylevy.{balance_history, deregistered_service_page, service_page}
import views.html.softdrinksindustrylevy.errors.{already_registered, invalid_affinity, invalid_role, registration_pending}
import views.html.{error_template, govuk_wrapper, main_template, time_out}
import views.softdrinksindustrylevy.errors.Errors
import views.uniform.Uniform

import scala.concurrent.ExecutionContext

trait CommonWiring {
  val configuration: Configuration
  val environment: Environment
  val messagesApi: MessagesApi
  val httpClient: HttpClient
  implicit val ec: ExecutionContext
  implicit val appConfig: AppConfig
  implicit val materializer: Materializer
  lazy val mode: Mode = environment.mode
  lazy val shortLivedCaching: ShortLivedHttpCaching = wire[SDILShortLivedCaching]
  lazy val keystore: SDILSessionCache = wire[SDILSessionCache]

  lazy val errors: Errors = wire[Errors]
  lazy val Views: Views = wire[Views]
  lazy val uniformHelpers: Uniform = wire[Uniform]
  lazy val viewHelpers: ViewHelpers = wire[ViewHelpers]
  lazy val govukTemplate: views.html.layouts.GovUkTemplate = wire[views.html.layouts.GovUkTemplate]

  lazy val alreadyRegistered: already_registered = wire[already_registered]
  lazy val invalidAffinity: invalid_affinity = wire[invalid_affinity]
  lazy val invalidRole: invalid_role = wire[invalid_role]
  lazy val registrationPending: registration_pending = wire[registration_pending]
  lazy val timeOut: time_out = wire[time_out]
  lazy val identify: views.html.softdrinksindustrylevy.register.identify =
    wire[views.html.softdrinksindustrylevy.register.identify]
  lazy val verify: views.html.softdrinksindustrylevy.register.verify =
    wire[views.html.softdrinksindustrylevy.register.verify]
  lazy val balanceHistory: balance_history = wire[balance_history]
  lazy val deregisteredServicePage: deregistered_service_page = wire[deregistered_service_page]
  lazy val servicePage: service_page = wire[service_page]
  lazy val errorTemplate: error_template = wire[error_template]

  lazy val updateBusinessAddresses: views.html.uniform.fragments.update_business_addresses =
    wire[views.html.uniform.fragments.update_business_addresses]
  lazy val ask: views.html.uniform.ask = wire[views.html.uniform.ask]
  lazy val cya: views.html.uniform.cya = wire[views.html.uniform.cya]
  lazy val end: views.html.uniform.end = wire[views.html.uniform.end]
  lazy val journeyEnd: views.html.uniform.journeyEnd = wire[views.html.uniform.journeyEnd]
  lazy val many: views.html.uniform.many = wire[views.html.uniform.many]
  lazy val tell: views.html.uniform.tell = wire[views.html.uniform.tell]

  lazy val main: main_template = wire[main_template]
  lazy val govUkWrapper: govuk_wrapper = wire[govuk_wrapper]

  //copied from uk.gov.hmrc.play.views.html.helpers
  lazy val address: Address = wire[Address]
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
  lazy val optimizelySnippet: OptimizelySnippet = wire[OptimizelySnippet]
  lazy val gtmSnippet: GTMSnippet = wire[GTMSnippet]
  lazy val serviceInfo: ServiceInfo = wire[ServiceInfo]
  lazy val sidebar: Sidebar = wire[Sidebar]

  val optimizelyConfig: OptimizelyConfig
  val assetConfig: AssetsConfig
  val gtmConfig: GTMConfig
  val accessibilityStatementConfig: AccessibilityStatementConfig
}
