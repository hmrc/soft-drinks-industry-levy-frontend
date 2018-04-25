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

import play.api.data.Forms.{boolean, mapping, seq}
import play.api.data.{Form, FormError, Mapping}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, Call, Result}
import sdil.actions.{VariationAction, VariationRequest}
import sdil.config.AppConfig
import sdil.controllers.variation.models.Sites
import sdil.forms.{FormHelpers, MappingWithExtraConstraint}
import sdil.models.backend.{Site, UkAddress}
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import uk.gov.voa.play.form.ConditionalMappings.mandatoryIfTrue
import views.html.softdrinksindustrylevy.variations.secondaryWarehouseWithRef

import scala.concurrent.Future

class WarehouseVariationController(val messagesApi: MessagesApi,
                                   cache: SessionCache,
                                   variationAction: VariationAction)
                                  (implicit config: AppConfig)
  extends FrontendController with I18nSupport with SiteRef {

  lazy val previousPage: Call = routes.VariationsController.show()

  def show: Action[AnyContent] = variationAction { implicit request =>
    val fillInitialForm: Form[Sites] = warehouses match {
      case Nil => ProductionSiteVariationController.form.fill(Sites(Nil, false, None))
      case _ => ProductionSiteVariationController.form
    }
    Ok(
      secondaryWarehouseWithRef(
        fillInitialForm,
        warehouses,
        request.data.previousPages.last,
        formTarget
      )
    )
  }

  def addSingleSite: Action[AnyContent] = variationAction.async { implicit request =>
    validateWith(initialForm)
  }

  def addMultipleSites: Action[AnyContent] = variationAction.async { implicit request =>
    validateWith(WarehouseVariationController.form)
  }

  private def validateWith(form: Form[Sites])(implicit request: VariationRequest[_]): Future[Result] = {
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
        case Sites(_, _, Some(addr)) =>
          val updatedSites = warehouses match {
            case addrs if addrs.nonEmpty =>
              addrs :+ Site(
                Some(nextRef(addrs)),
                UkAddress.fromAddress(addr)
              )
            case addrs => Seq(Site(
              Some(1.toString),
              UkAddress.fromAddress(addr)
            ))
          }
          cache.cache("variationData", request.data.copy(updatedWarehouseSites = updatedSites)) map { _ =>
            Redirect(routes.WarehouseVariationController.show())
          }
        case Sites(addresses, _, _) =>
          val updated = request.data.copy(updatedWarehouseSites = addresses)

          cache.cache("variationData", updated) map { _ =>
            Redirect(routes.VariationsController.show())
          }
      }
    )
  }

  private def warehouses(implicit request: VariationRequest[_]): Seq[Site] = {
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

  val form: Form[Sites] = Form(warehouseSitesMapping)

  private lazy val warehouseSitesMapping: Mapping[Sites] = new MappingWithExtraConstraint[Sites] {
    override val underlying: Mapping[Sites] = mapping(
      "additionalSites" -> seq(siteJsonMapping),
      "addAddress" -> boolean,
      "additionalAddress" -> mandatoryIfTrue("addAddress", addressMapping)
    )(Sites.apply)(Sites.unapply)

    override def bind(data: Map[String, String]): Either[Seq[FormError], Sites] = {
      underlying.bind(data) match {
        case Left(errs) => Left(errs)
        case Right(sites) => Right(sites)
      }
    }
  }

}