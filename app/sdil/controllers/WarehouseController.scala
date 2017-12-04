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

import java.time.LocalDate

import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, Call, Request}
import sdil.config.AppConfig
import sdil.forms.WarehouseForm
import sdil.models.{Address, Packaging, SecondaryWarehouse}
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.bootstrap.controller.FrontendController

import scala.concurrent.Future

class WarehouseController(val messagesApi: MessagesApi, cache: SessionCache)(implicit config: AppConfig)
  extends FrontendController with I18nSupport {

  def secondaryWarehouse = Action.async { implicit request =>
    for {
      addrs <- getWarehouseAddresses
      backLink <- getBackLink
    } yield {
      Ok(views.html.softdrinksindustrylevy.register.secondaryWarehouse(WarehouseForm(), addrs, backLink))
    }
  }

  def validate = Action.async { implicit request =>
    getWarehouseAddresses flatMap { addrs =>
      WarehouseForm().bindFromRequest().fold(
        errors => getBackLink map { link =>
          BadRequest(views.html.softdrinksindustrylevy.register.secondaryWarehouse(errors, addrs, link))
        },
        {
          case SecondaryWarehouse(_, Some(addr)) => cache.cache("secondaryWarehouses", addrs :+ addr) map { _ =>
            Redirect(routes.WarehouseController.secondaryWarehouse())
          }
          case _ => Redirect(routes.ContactDetailsController.displayContactDetails())
        }
      )
    }
  }

  def remove(idx: Int) = Action.async { implicit request =>
    getWarehouseAddresses flatMap { addrs =>
      val updated = addrs.take(idx) ++ addrs.drop(idx + 1)
      cache.cache("secondaryWarehouses", updated) map { _ =>
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

  private def getBackLink(implicit request: Request[_]): Future[Call] = {
    cache.fetchAndGetEntry[Packaging]("packaging") flatMap {
      case Some(p) if p.isLiable => routes.ProductionSiteController.addSite()
      case Some(p) => backToStartDate
      case _ => routes.PackageController.displayPackage()
    }
  }

  private def backToStartDate(implicit request: Request[_]): Future[Call] = {
    if (LocalDate.now.isBefore(config.taxStartDate)) {
      cache.fetchAndGetEntry[Boolean]("import") map {
        case Some(true) => routes.LitreageController.show("importVolume")
        case _ => routes.ImportController.display()
      }
    } else {
      routes.StartDateController.displayStartDate()
    }
  }
}
