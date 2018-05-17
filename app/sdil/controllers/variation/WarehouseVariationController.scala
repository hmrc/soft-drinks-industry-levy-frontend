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

package sdil.controllers.variation

import play.api.data.Forms._
import play.api.data.{Form, FormError, Mapping}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, Call, Result}
import sdil.actions.{VariationAction, VariationRequest}
import sdil.config.AppConfig
import sdil.controllers.SiteRef
import sdil.forms.{FormHelpers, MappingWithExtraConstraint}
import sdil.models.Sites
import sdil.models.backend.{UkAddress, WarehouseSite}
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import uk.gov.voa.play.form.ConditionalMappings._
import views.html.softdrinksindustrylevy.variations.{retrieve_summary_secondaryWarehouse, secondaryWarehouseWithRef}

import scala.concurrent.Future



class WarehouseVariationController(val messagesApi: MessagesApi,
                                   cache: SessionCache,
                                   variationAction: VariationAction)
                                  (implicit config: AppConfig)
  extends FrontendController with I18nSupport with SiteRef {

  def show: Action[AnyContent] = variationAction { implicit request =>
    (request.data.isVoluntary, warehouses) match {
      case (true, _) =>
        Redirect(routes.VariationsController.show())
      case (_, Nil) =>
        formPage(WarehouseVariationController.form.fill(Sites[WarehouseSite](Nil, false, None, None)))
      case _ =>
        formPage(WarehouseVariationController.form)
    }
  }

  private def formPage(form: Form[Sites[WarehouseSite]])(implicit request: VariationRequest[AnyContent]): Result = {
    Ok(
      secondaryWarehouseWithRef(
        form,
        warehouses,
        request.data.previousPages.last,
        formTarget
      )
    )
  }

  def addSingleSite: Action[AnyContent] = variationAction.async { implicit request =>
    validateWith(WarehouseVariationController.initialForm)
  }

  def addMultipleSites: Action[AnyContent] = variationAction.async { implicit request =>
    validateWith(WarehouseVariationController.form)
  }

  private def validateWith(form: Form[Sites[WarehouseSite]])(implicit request: VariationRequest[_]): Future[Result] = {
    form.bindFromRequest().fold(
      errors => Future(BadRequest(
        secondaryWarehouseWithRef(
          errors,
          warehouses,
          request.data.previousPages.last,
          formTarget
        )
      )),
      {
        case Sites(_, true, tradingName, Some(addr)) =>
          val updatedSites = warehouses match {
            case addrs if addrs.nonEmpty =>
              addrs :+ WarehouseSite(
                UkAddress.fromAddress(addr),
                Some(nextRef(request.data.original.warehouseSites, addrs)),
                tradingName,
                None
              )
            case addrs => Seq(WarehouseSite(
              UkAddress.fromAddress(addr),
              Some(nextRef(request.data.original.warehouseSites, addrs)),
              tradingName,
              None
            ))
          }
          cache.cache("variationData", request.data.copy(updatedWarehouseSites = updatedSites)) map { _ =>
            Redirect(routes.WarehouseVariationController.show())
          }
        case Sites(addresses:List[WarehouseSite], _, _, _) =>
          val updated = request.data.copy(updatedWarehouseSites = addresses)

          cache.cache("variationData", updated) map { _ =>
            Redirect(routes.WarehouseVariationController.confirm())
          }
      }
    )
  }

  def confirm: Action[AnyContent] = variationAction { implicit request =>
    Ok(retrieve_summary_secondaryWarehouse(request.data))
  }

  private def warehouses(implicit request: VariationRequest[_]): Seq[WarehouseSite] = {
    request.data.updatedWarehouseSites
  }

  private def formTarget(implicit request: VariationRequest[_]): Call = {
    warehouses match {
      case Nil => routes.WarehouseVariationController.addSingleSite()
      case _ => routes.WarehouseVariationController.addMultipleSites()
    }
  }
}

object WarehouseVariationController extends FormHelpers {

  val form: Form[Sites[WarehouseSite]] = Form(warehouseSitesMapping)

  val initialForm: Form[Sites[WarehouseSite]] = Form(
    mapping(
      "additionalSites" -> ignored(Seq.empty[WarehouseSite]),
      "addAddress" -> mandatoryBoolean,
      "tradingName" -> optional(tradingNameMapping),
      "additionalAddress" -> mandatoryIfTrue("addAddress", addressMapping)
    )(Sites.apply)(Sites.unapply)
  )

  private lazy val warehouseSitesMapping: Mapping[Sites[WarehouseSite]] = new MappingWithExtraConstraint[Sites[WarehouseSite]] {
    override val underlying: Mapping[Sites[WarehouseSite]] = mapping(
      "additionalSites" -> seq(warehouseSiteJsonMapping),
      "addAddress" -> boolean,
      "tradingName" -> optional(tradingNameMapping),
      "additionalAddress" -> mandatoryIfTrue("addAddress", addressMapping)
    )(Sites.apply)(Sites.unapply)

    override def bind(data: Map[String, String]): Either[Seq[FormError], Sites[WarehouseSite]] = {
      underlying.bind(data) match {
        case Left(errs) => Left(errs)
        case Right(sites) => Right(sites)
      }
    }
  }

}