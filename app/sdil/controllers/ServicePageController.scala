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

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class ServicePageController(
  val messagesApi: MessagesApi,
  sdilConnector: SoftDrinksIndustryLevyConnector,
  registeredAction: RegisteredAction,
  errorHandler: FrontendErrorHandler)
  (implicit config: AppConfig,
   val ec: ExecutionContext)
  extends FrontendController with I18nSupport {

  def show: Action[AnyContent] = registeredAction.async { implicit request =>


    val sdilRef = request.sdilEnrolment.value
    val ret = for {
      subscription  <- OptionT(sdilConnector.retrieveSubscription(sdilRef))
      returnPeriods <- OptionT(sdilConnector.returns_pending(subscription.utr).map(_.some))
      lastReturn    <- OptionT(sdilConnector.returns_get(subscription.utr, ReturnPeriod(LocalDate.now).previous).map(_.some))
      deregCheck    = subscription.deregDate.getOrElse(LocalDate.now.plusYears(100))
      pendingDereg    <- OptionT(sdilConnector.returns_get(subscription.utr, ReturnPeriod(deregCheck)).map(_.some))
      variableReturns <- OptionT(sdilConnector.returns_variable(subscription.utr).map(_.some))
      interesting   <- OptionT(sdilConnector.balanceHistory(sdilRef, withAssessment = true).map(x=>interest(x).some))
      balance       <- OptionT(sdilConnector.balance(sdilRef, withAssessment = true).map(_.some))
    } yield {
      val addr = Address.fromUkAddress(subscription.address)
      if(subscription.deregDate.nonEmpty){
        Ok(deregistered_service_page(addr, subscription, lastReturn, balance, pendingDereg, variableReturns))
      } else {
        Ok(service_page(addr, request.sdilEnrolment.value, subscription, returnPeriods, lastReturn, balance, interesting))
      }
    }
    ret.getOrElse { NotFound(errorHandler.notFoundTemplate) }
  }

  def interest(items: List[FinancialLineItem]): BigDecimal =
    items.distinct.collect {
      case a : Interest => a.amount
    }.sum

  def balanceHistory: Action[AnyContent] = registeredAction.async { implicit request =>

    val sdilRef = request.sdilEnrolment.value

    sdilConnector.balanceHistory(sdilRef, withAssessment = true) >>= { items =>
      val itemsWithRunningTotal = listItemsWithTotal(items)
      val total = extractTotal(itemsWithRunningTotal)

      val ret = for {
        subscription <- OptionT(sdilConnector.retrieveSubscription(sdilRef))
        deregCheck = subscription.deregDate.getOrElse(LocalDate.now.plusYears(100))

        pendingDereg <- OptionT(sdilConnector.returns_get(subscription.utr, ReturnPeriod(deregCheck)).map(_.some))
      } yield {
        Ok(
          balance_history(
            subscription.orgName,
            itemsWithRunningTotal,
            total,
            request.sdilEnrolment.value,
            subscription.deregDate,
            pendingDereg,
            interest(items))
        )
      }
      ret.getOrElse { NotFound(errorHandler.notFoundTemplate) }
    }
  }
}
