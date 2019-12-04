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

import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import sdil.actions.RegisteredAction
import sdil.config.{AppConfig, FrontendAppConfig}
import sdil.connectors.{PayApiConnector, SoftDrinksIndustryLevyConnector, SpjRequestBtaSdil}
import uk.gov.hmrc.play.bootstrap.controller.FrontendController

import scala.concurrent.ExecutionContext

class PaymentController(
  payApiConnector: PayApiConnector,
  sdilConnector: SoftDrinksIndustryLevyConnector,
  registeredAction: RegisteredAction,
  frontendAppConfig: FrontendAppConfig,
  fcc: MessagesControllerComponents)(implicit config: AppConfig, val ec: ExecutionContext)
    extends FrontendController(fcc) {

  private def balanceToPaymentPrepopulateAmount(balance: BigDecimal): Long = {
    val balanceInPence = balance * 100
    val amountOwed = balanceInPence * -1
    amountOwed.toLongExact
  }

  def payNow(): Action[AnyContent] = registeredAction.async { implicit request =>
    val sdilRef = request.sdilEnrolment.value
    sdilConnector.balance(sdilRef, withAssessment = true).flatMap { balance =>
      val spjRequestBtaSdil = SpjRequestBtaSdil(
        sdilRef,
        balanceToPaymentPrepopulateAmount(balance),
        frontendAppConfig.sdilHomePage,
        frontendAppConfig.sdilHomePage
      )
      payApiConnector.getSdilPayLink(spjRequestBtaSdil).map(nextUrl => Redirect(nextUrl.nextUrl))
    }
  }
}
