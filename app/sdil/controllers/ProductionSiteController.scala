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

import play.api.data.Forms._
import play.api.data.{Form, FormError, Mapping}
import play.api.i18n.{I18nSupport, MessagesApi}
import sdil.actions.{FormAction, RegistrationFormRequest}
import sdil.config.{AppConfig, RegistrationFormDataCache}
import sdil.forms.{FormHelpers, MappingWithExtraConstraint}
import sdil.models.DetailsCorrect.DifferentAddress
import sdil.models._
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import uk.gov.voa.play.form.ConditionalMappings.mandatoryIfTrue
import views.html.softdrinksindustrylevy.register.productionSite

class ProductionSiteController(val messagesApi: MessagesApi, cache: RegistrationFormDataCache, formAction: FormAction)
                              (implicit config: AppConfig)
  extends FrontendController with I18nSupport {

  import ProductionSiteController._

  def show = formAction.async { implicit request =>
    Journey.expectedPage(ProductionSitesPage) match {
      case ProductionSitesPage => Ok(
        productionSite(
          form,
          request.formData.rosmData.address,
          primaryPlaceOfBusiness,
          request.formData.productionSites.getOrElse(Nil),
          Journey.previousPage(ProductionSitesPage).show
        )
      )
      case otherPage => Redirect(otherPage.show)
    }
  }

  def submit = formAction.async { implicit request =>
    form.bindFromRequest().fold(
      errors => BadRequest(
        productionSite(
          errors,
          request.formData.rosmData.address,
          primaryPlaceOfBusiness,
          request.formData.productionSites.getOrElse(Nil),
          Journey.previousPage(ProductionSitesPage).show
        )
      ),
      {
        case ProductionSites(bprAddress, ppobAddress, _, true, Some(additionalAddress)) =>
          val updated = Seq(bprAddress, ppobAddress).flatten.map(Address.fromString) ++
            request.formData.productionSites.getOrElse(Nil) :+
            additionalAddress

          cache.cache(request.internalId, request.formData.copy(productionSites = Some(updated))) map { _ =>
            Redirect(routes.ProductionSiteController.show())
          }

        case p =>
          val addresses = (Seq(p.bprAddress, p.ppobAddress).flatten ++ p.additionalSites).map(Address.fromString)
          val updated = request.formData.copy(productionSites = Some(addresses))

          cache.cache(request.internalId, updated) map { _ =>
            Redirect(Journey.nextPage(ProductionSitesPage, updated).show)
          }
      }
    )
  }

  private def primaryPlaceOfBusiness(implicit request: RegistrationFormRequest[_]): Option[Address] = {
    request.formData.verify flatMap {
      case DifferentAddress(a) => Some(a)
      case _ => None
    }
  }
}

object ProductionSiteController extends FormHelpers {

  val form: Form[ProductionSites] = Form(productionSitesMapping)

  private lazy val productionSitesMapping: Mapping[ProductionSites] = new MappingWithExtraConstraint[ProductionSites] {
    override val underlying: Mapping[ProductionSites] = mapping(
      "bprAddress" -> optional(text),
      "ppobAddress" -> optional(text),
      "additionalSites" -> seq(text),
      "addAddress" -> boolean,
      "additionalAddress" -> mandatoryIfTrue("addAddress", addressMapping)
    )(ProductionSites.apply)(ProductionSites.unapply)

    override def bind(data: Map[String, String]): Either[Seq[FormError], ProductionSites] = {
      underlying.bind(data) match {
        case Left(errs) => Left(errs)
        case Right(sites) if noSitesSelected(sites) => Left(Seq(FormError("productionSites", "error.no-production-sites")))
        case Right(sites) => Right(sites)
      }
    }
  }

  private lazy val noSitesSelected: ProductionSites => Boolean = {
    p => p.bprAddress.isEmpty && p.ppobAddress.isEmpty && p.additionalAddress.isEmpty && p.additionalSites.isEmpty
  }

  case class ProductionSites(bprAddress: Option[String],
                             ppobAddress: Option[String],
                             additionalSites: Seq[String],
                             addAddress: Boolean,
                             additionalAddress: Option[Address])
}