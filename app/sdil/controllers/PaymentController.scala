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

import java.time.LocalDate

import cats.data.OptionT
import cats.implicits._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, ControllerComponents, MessagesControllerComponents}
import sdil.actions.RegisteredAction
import sdil.config.{AppConfig, FrontendAppConfig}
import sdil.connectors.{PayApiConnector, SoftDrinksIndustryLevyConnector, SpjRequestBtaSdil}
import sdil.models._
import uk.gov.hmrc.play.bootstrap.controller.{BackendController, FrontendController}
import uk.gov.hmrc.play.bootstrap.http.FrontendErrorHandler
import views.html.softdrinksindustrylevy._

import scala.concurrent.{ExecutionContext, Future}

class PaymentController(
  payApiConnector: PayApiConnector,
  sdilConnector: SoftDrinksIndustryLevyConnector,
  registeredAction: RegisteredAction,
  frontendAppConfig: FrontendAppConfig,
  fcc: MessagesControllerComponents)(implicit config: AppConfig, val ec: ExecutionContext)
    extends FrontendController(fcc) {

  def payNow(): Action[AnyContent] = registeredAction.async { implicit request =>
    val sdilRef = request.sdilEnrolment.value
    sdilConnector.balance(sdilRef, withAssessment = true).flatMap { balance =>
      val spjRequestBtaSdil = SpjRequestBtaSdil(
        sdilRef,
        (balance * 100).toLongExact,
        frontendAppConfig.sdilHomePage,
        frontendAppConfig.sdilHomePage
      )
      payApiConnector.getSdilPayLink(spjRequestBtaSdil).map(nextUrl => Redirect(nextUrl.nextUrl))
    }
  }

//    val sdilRef = request.sdilEnrolment.value
//    val ret = for {
//      subscription  <- OptionT(sdilConnector.retrieveSubscription(sdilRef))
//      returnPeriods <- OptionT.liftF(sdilConnector.returns_pending(subscription.utr))
//      lastReturn    <- OptionT.liftF(sdilConnector.returns_get(subscription.utr, ReturnPeriod(LocalDate.now).previous))
//      isDereg = subscription.deregDate.nonEmpty
//      deDatePeriod = subscription.deregDate.getOrElse(LocalDate.now.plusYears(500))
//      pendingDereg <- OptionT.liftF(
//                       if (isDereg)
//                         sdilConnector.returns_get(subscription.utr, ReturnPeriod(deDatePeriod))
//                       else
//                         Future.successful(None)
//                     )
//      variableReturns <- OptionT.liftF(sdilConnector.returns_variable(subscription.utr))
//      interesting     <- OptionT(sdilConnector.balanceHistory(sdilRef, withAssessment = true).map(x => interest(x).some))
//      balance         <- OptionT(sdilConnector.balance(sdilRef, withAssessment = true).map(_.some))
//    } yield {
//      val addr = Address.fromUkAddress(subscription.address)
//      if (subscription.deregDate.nonEmpty) {
//        Ok(deregistered_service_page(addr, subscription, lastReturn, balance, pendingDereg, variableReturns))
//      } else {
//        Ok(
//          service_page(
//            addr,
//            request.sdilEnrolment.value,
//            subscription,
//            returnPeriods,
//            lastReturn,
//            balance,
//            interesting))
//      }
//    }
//    ret.getOrElse { NotFound(errorHandler.notFoundTemplate) }
}
