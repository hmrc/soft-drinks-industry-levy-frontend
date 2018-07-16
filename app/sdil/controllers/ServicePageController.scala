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

package sdil.controllers

import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent}
import sdil.actions.RegisteredAction
import sdil.config.AppConfig
import sdil.connectors.SoftDrinksIndustryLevyConnector
import sdil.models._
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import uk.gov.hmrc.play.bootstrap.http.FrontendErrorHandler
import views.html.softdrinksindustrylevy._
import cats.implicits._
import cats.data.OptionT
import scala.concurrent._
import java.time.LocalDate

class ServicePageController(val messagesApi: MessagesApi,
                            sdilConnector: SoftDrinksIndustryLevyConnector,
                            registeredAction: RegisteredAction,
                            errorHandler: FrontendErrorHandler)
                           (implicit config: AppConfig)
  extends FrontendController with I18nSupport {

  def show: Action[AnyContent] = registeredAction.async { implicit request =>

    type FutOpt[A] = OptionT[Future, A]

    val sdilRef = request.sdilEnrolment.value
    val ret = for {
      subscription  <- OptionT(sdilConnector.retrieveSubscription(sdilRef))
      returnPeriods <- if (config.returnsEnabled)
                         OptionT(sdilConnector.returns.pending(subscription.utr).map(_.some))
                       else
                         Nil.pure[FutOpt]
      balance       <- if (config.balanceEnabled)
                         OptionT(sdilConnector.balance(sdilRef).map(_.some))
                       else BigDecimal(0).pure[FutOpt]
    } yield {
      val addr = Address.fromUkAddress(subscription.address)
      Ok(service_page(addr, request.sdilEnrolment.value, subscription, returnPeriods, balance))
    }

    ret.getOrElse { NotFound(errorHandler.notFoundTemplate) }
  }

  def balanceHistory: Action[AnyContent] = registeredAction.async { implicit request =>

    if(!config.balanceEnabled)
      throw new NotImplementedError("Balance page is not enabled")

    val sdilRef = request.sdilEnrolment.value

    sdilConnector.balanceHistory(request.sdilEnrolment.value) >>= { items =>

      val itemsWithRunningTotal =
        items.foldLeft(List.empty[(FinancialLineItem,BigDecimal)]){
          (acc,n) => (n,acc.headOption.fold(n.amount)(_._2 + n.amount)) :: acc
        }.reverse
      Ok(balance_history(itemsWithRunningTotal))
    }
  }
}
