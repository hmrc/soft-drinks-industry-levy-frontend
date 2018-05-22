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
import play.api.mvc.{Action, AnyContent}
import sdil.actions.VariationAction
import sdil.config.AppConfig
import sdil.controllers.SiteRef
import sdil.forms.{FormHelpers, MappingWithExtraConstraint}
import sdil.models.Sites
import sdil.models.backend.{Site, UkAddress}
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import uk.gov.voa.play.form.ConditionalMappings.mandatoryIfTrue
import views.html.softdrinksindustrylevy.variations.{productionSiteWithRef, retrieve_summary_productionSites}

import scala.concurrent.Future

class ProductionSiteVariationController (val messagesApi: MessagesApi,
                                         cache: SessionCache,
                                         variationAction: VariationAction)
                                        (implicit config: AppConfig)
  extends FrontendController with I18nSupport with SiteRef {

  def show: Action[AnyContent] = variationAction { implicit request =>
    if (request.data.isVoluntary) {
      Redirect(routes.VariationsController.show())
    } else {
      Ok(
        productionSiteWithRef(
          ProductionSiteVariationController.form,
          request.data.updatedProductionSites,
          request.data.previousPages.last,
          routes.ProductionSiteVariationController.submit()
        )
      )
    }
  }

  def submit: Action[AnyContent] = variationAction.async { implicit request =>
    ProductionSiteVariationController.form.bindFromRequest().fold(
      errors => Future(BadRequest(
        productionSiteWithRef(
          errors,
          request.data.updatedProductionSites,
          request.data.previousPages.last,
          routes.ProductionSiteVariationController.submit()
        )
      )),
      {
        case Sites(_, true, tradingName, Some(additionalAddress)) =>
          val siteRef = nextRef(request.data.original.productionSites, request.data.updatedProductionSites)
          val site = Site(UkAddress.fromAddress(additionalAddress), Some(siteRef), tradingName, None)
          val updated = request.data.updatedProductionSites :+ site
          cache.cache("variationData", request.data.copy(updatedProductionSites = updated)) map { _ =>
            Redirect(routes.ProductionSiteVariationController.show())
          }

        case Sites(sites:List[Site], _, _, _) =>
          val updated = request.data.copy(updatedProductionSites = sites)
          cache.cache("variationData", updated) map { _ =>
            Redirect(routes.ProductionSiteVariationController.confirm())
          }
      }
    )
  }

  def confirm: Action[AnyContent] = variationAction { implicit request =>
    Ok(retrieve_summary_productionSites(request.data))
  }

}


object ProductionSiteVariationController extends FormHelpers {

  val form: Form[Sites] = Form(productionSitesMapping)

  private lazy val productionSitesMapping: Mapping[Sites] = new MappingWithExtraConstraint[Sites] {
    override val underlying: Mapping[Sites] = mapping(
      "additionalSites" -> seq(siteJsonMapping),
      "addAddress" -> boolean,
      "tradingName" -> optional(tradingNameMapping),
      "additionalAddress" -> mandatoryIfTrue("addAddress", addressMapping)
    )(Sites.apply)(Sites.unapply)

    override def bind(data: Map[String, String]): Either[Seq[FormError], Sites] = {
      underlying.bind(data) match {
        case Left(errs) => Left(errs)
        case Right(sites) if noSitesSelected(sites) => Left(Seq(FormError("productionSites", "error.no-production-sites")))
        case Right(sites) => Right(sites)
      }
    }
  }

  private lazy val noSitesSelected: Sites => Boolean = {
    p => p.sites.isEmpty && p.additionalSites.isEmpty
  }

}