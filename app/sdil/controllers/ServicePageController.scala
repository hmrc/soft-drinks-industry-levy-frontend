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

import sdil.models.backend.Subscription
import sdil.models.retrieved.RetrievedSubscription
import views.html.uniform.fragments.update_business_addresses

class ServicePageController(
  val messagesApi: MessagesApi,
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
      returnPeriods <- OptionT(sdilConnector.returns.pending(subscription.utr).map(_.some))
      lastReturn    <- OptionT(sdilConnector.returns.get(subscription.utr, ReturnPeriod(LocalDate.now).previous).map(_.some))
      balance       <- OptionT(sdilConnector.balance(sdilRef).map(_.some))
    } yield {
      val addr = Address.fromUkAddress(subscription.address)
      Ok(service_page(addr, request.sdilEnrolment.value, subscription, returnPeriods, lastReturn, balance))
    }
    ret.getOrElse { NotFound(errorHandler.notFoundTemplate) }

  }

  def amendBusinessAddresses: Action[AnyContent] = registeredAction.async { implicit request =>
    val sdilRef = request.sdilEnrolment.value

    sdilConnector.retrieveSubscription(sdilRef).flatMap {
      case Some(subscription) =>
        val addr = Address.fromUkAddress(subscription.address)
        Ok(update_business_addresses(subscription, addr))
      case None =>
        NotFound(errorHandler.notFoundTemplate)
    }
  }

  def balanceHistory: Action[AnyContent] = registeredAction.async { implicit request =>

    val sdilRef = request.sdilEnrolment.value

    sdilConnector.balanceHistory(request.sdilEnrolment.value) >>= { items =>

      val itemsWithRunningTotal =
        items.foldLeft(List.empty[(FinancialLineItem, BigDecimal)]) {
          (acc, n) => (n, acc.headOption.fold(n.amount)(_._2 + n.amount)) :: acc
        }
      val total = itemsWithRunningTotal.headOption.fold(BigDecimal(0))(_._2)
      Ok(balance_history(itemsWithRunningTotal, total, request.sdilEnrolment.value))
    }
  }
}
