/*
 * Copyright 2022 HM Revenue & Customs
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

package views

import javax.inject.Inject
import uk.gov.hmrc.play.views.html.helpers._
import uk.gov.hmrc.play.views.html.layouts._
import play.api.Configuration
import uk.gov.hmrc.play.config._

private[views] case class ViewHelpers(config: Configuration) {
  lazy val form = new FormWithCSRF
  lazy val reportAProblemLink = new ReportAProblemLink
  lazy val article = new Article
  lazy val footer = new Footer(assetsConfig)
  lazy val headWithTrackingConsent = {
    val trackingConfig = new TrackingConsentConfig(config)
    val optimiselyConfig = new OptimizelyConfig(config)
    val snippet = new TrackingConsentSnippet(trackingConfig, optimiselyConfig)
    new HeadWithTrackingConsent(snippet, assetsConfig)
  }
  lazy val headerNav = new HeaderNav
  lazy val mainContent = new MainContent
  lazy val mainContentHeader = new MainContentHeader
  lazy val serviceInfo = new ServiceInfo
  lazy val sidebar = new Sidebar
  lazy val assetsConfig = new AssetsConfig(config)
  lazy val footerLinks = new FooterLinks(accessibilityConfig)
  lazy val accessibilityConfig = new AccessibilityStatementConfig(config)
}
