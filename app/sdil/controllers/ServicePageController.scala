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
import views.html.softdrinksindustrylevy.service_page
import cats.implicits._
import cats.data.OptionT
import scala.concurrent._

class ServicePageController(val messagesApi: MessagesApi,
                            sdilConnector: SoftDrinksIndustryLevyConnector,
                            registeredAction: RegisteredAction,
                            errorHandler: FrontendErrorHandler)
                           (implicit config: AppConfig)
  extends FrontendController with I18nSupport {

  def show: Action[AnyContent] = registeredAction.async { implicit request =>

    type FutOpt[A] = OptionT[Future, A]

    val ret = for {
      subscription <- OptionT(sdilConnector.retrieveSubscription(request.sdilEnrolment.value))
      returnPeriods <- if (config.returnsEnabled)
                         OptionT(sdilConnector.returns.pending(subscription.utr).map(_.some))
                       else 
                         Nil.pure[FutOpt]
    } yield {
      val addr = Address.fromUkAddress(subscription.address)
      Ok(service_page(addr, request.sdilEnrolment.value, subscription, returnPeriods))
    }

    ret.getOrElse { NotFound(errorHandler.notFoundTemplate) }
  }
}
