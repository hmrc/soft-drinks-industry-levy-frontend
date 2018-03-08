/*
 * Copyright 2018 HM Revenue & Customs
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

package sdil.connectors

import java.net.URLEncoder

import play.api.Mode.Mode
import play.api.{Configuration, Environment}
import sdil.controllers.BetaRegistration
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}

class ContactFrontendConnector(http: HttpClient,
                               val runModeConfiguration: Configuration,
                               val environment: Environment) extends ServicesConfig {

  override protected def mode: Mode = environment.mode

  lazy val contactFrontend: String = baseUrl("contact-frontend")

  def submitBetaRegistrationRequest(registration: BetaRegistration)
                                   (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] = {
    val headers = hc.withExtraHeaders("Csrf-Token" -> "nocheck")

    //shouldn't be necessary, but is a required parameter
    val resubmitUrl = URLEncoder.encode(sdil.controllers.routes.RegisterForBetaController.show().url, "UTF-8")

    val payload = Map(
      "contact-name" -> Seq(registration.businessName),
      "contact-email" -> Seq(registration.email),
      "contact-comments" ->
        Seq(
          s"""Request to register for SDIL private beta.=
             |
             |Company name: ${registration.businessName}
             |
             |UTR: ${registration.utr}
             |
             |Phone number: ${registration.phoneNumber}""".stripMargin
        ),
      "isJavascript" -> Seq("false"),
      "referer" -> Seq("soft-drinks-industry-levy-liability-tool"),
      "csrfToken" -> Seq("nocheck"),
      "service" -> Seq("soft-drinks-industry-levy-liability-tool")
    )
    val submitUrl = s"$contactFrontend/contact/contact-hmrc/form?resubmitUrl=$resubmitUrl"

    http.POSTForm[HttpResponse](submitUrl, payload)(implicitly, headers, ec) map { _ => () }
  }
}
