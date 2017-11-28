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
import sdil.forms.ProductionSiteForm
import sdil.models.{Address, ProductionSite}
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.frontend.controller.FrontendController

import scala.concurrent.Future

class ProductionSiteController @Inject()(val messagesApi: MessagesApi) extends FrontendController with I18nSupport {

  val cache: SessionCache = FormDataCache

  def addSite = Action.async { implicit request =>
    //FIXME look up address record
    getOtherSites map { addrs =>
      Ok(views.html.softdrinksindustrylevy.register.productionSite(ProductionSiteForm(), fakeAddress, addrs))
    }
  }

  def validate = Action.async { implicit request =>
    getOtherSites flatMap { addrs =>
      ProductionSiteForm().bindFromRequest().fold(
        errors => BadRequest(views.html.softdrinksindustrylevy.register.productionSite(errors, fakeAddress, addrs)),
        {
          case ProductionSite(_, Some(addr)) => cache.cache("productionSites", addrs :+ addr) map { _ =>
            Redirect(routes.ProductionSiteController.addSite())
          }
          case _ => Redirect(routes.WarehouseController.secondaryWarehouse())
        }
      )
    }
  }

  def remove(idx: Int) = Action.async { implicit request =>
    getOtherSites flatMap { addrs =>
      val updated = addrs.take(idx) ++ addrs.drop(idx + 1)
      cache.cache("productionSites", updated) map { _ =>
        Redirect(routes.ProductionSiteController.addSite())
      }
    }
  }

  private def getOtherSites(implicit request: Request[_]): Future[Seq[Address]] = {
    cache.fetchAndGetEntry[Seq[Address]]("productionSites") map {
      case Some(Nil) | None => Nil
      case Some(addrs) => addrs
    }
  }

  private lazy val fakeAddress = Address("an address", "somewhere", "", "", "AA11 1AA")

}
