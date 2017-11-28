/*
 * Copyright 2017 HM Revenue & Customs
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

import javax.inject.Inject

import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, Request}
import sdil.config.FormDataCache
import sdil.forms.WarehouseForm
import sdil.models.{Address, SecondaryWarehouse}
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.frontend.controller.FrontendController

import scala.concurrent.Future

class WarehouseController @Inject()(val messagesApi: MessagesApi) extends FrontendController with I18nSupport {

  val cache: SessionCache = FormDataCache

  def secondaryWarehouse = Action.async { implicit request =>
    getWarehouseAddresses map { addrs =>
      Ok(views.html.softdrinksindustrylevy.register.secondaryWarehouse(WarehouseForm(), addrs))
    }
  }

  def validate = Action.async { implicit request =>
    getWarehouseAddresses flatMap { addrs =>
      WarehouseForm().bindFromRequest().fold(
        errors => BadRequest(views.html.softdrinksindustrylevy.register.secondaryWarehouse(errors, addrs)),
        {
          case SecondaryWarehouse(_, Some(addr)) => cache.cache("secondaryWarehouses", addrs :+ addr) map { _ =>
            Redirect(routes.WarehouseController.secondaryWarehouse())
          }
          case _ => Redirect(routes.SDILController.displayContactDetails())
        }
      )
    }
  }

  def remove(idx: Int) = Action.async { implicit request =>
    getWarehouseAddresses flatMap { addrs =>
      val v = addrs.take(idx) ++ addrs.drop(idx + 1)
      cache.cache("secondaryWarehouses", v) map { _ =>
        Redirect(routes.WarehouseController.secondaryWarehouse())
      }
    }
  }

  private def getWarehouseAddresses(implicit request: Request[_]): Future[Seq[Address]] = {
    cache.fetchAndGetEntry[Seq[Address]]("secondaryWarehouses") map {
      case None | Some(Nil) => Nil
      case Some(addrs) => addrs
    }
  }
}
