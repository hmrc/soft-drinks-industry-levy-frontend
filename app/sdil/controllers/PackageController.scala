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

import play.api.data.Forms.{boolean, mapping}
import play.api.data.validation.Constraint
import play.api.data.{Form, FormError, Mapping}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent}
import sdil.actions.FormAction
import sdil.config.{AppConfig, FormDataCache}
import sdil.forms.FormHelpers
import sdil.models.{PackagePage, Packaging, RegistrationFormData}
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import views.html.softdrinksindustrylevy.register

class PackageController(val messagesApi: MessagesApi, cache: FormDataCache, formAction: FormAction)(implicit config: AppConfig)
  extends FrontendController with I18nSupport {

  import PackageController._

  def show(): Action[AnyContent] = formAction.async { implicit request =>
    PackagePage.expectedPage(request.formData) match {
      case PackagePage => Ok(register.packagePage(request.formData.packaging.fold(form)(form.fill)))
      case otherPage => Redirect(otherPage.show)
    }
  }

  def submit(): Action[AnyContent] = formAction.async { implicit request =>
    form.bindFromRequest.fold(
      formWithErrors => BadRequest(register.packagePage(formWithErrors)),
      packaging => {
        val updated = updateData(request.formData, packaging)
        cache.cache(request.internalId, updated) map { _ =>
          Redirect(PackagePage.nextPage(updated).show)
        }
      }
    )
  }

  //clear unneeded data from session cache when the user's answers change
  private def updateData(formData: RegistrationFormData, packaging: Packaging) = packaging match {
    case Packaging(false, _, _) =>
      formData.copy(
        packaging = Some(Packaging(false, false, false)),
        volumeForOwnBrand = None,
        volumeForCustomerBrands = None,
        productionSites = None
      )
    case Packaging(true, true, false) =>
      formData.copy(packaging = Some(packaging), volumeForCustomerBrands = None)
    case Packaging(true, false, true) =>
      formData.copy(packaging = Some(packaging), volumeForOwnBrand = None)
    case _ => formData.copy(packaging = Some(packaging))
  }

}

object PackageController extends FormHelpers {
  val form = Form(packageMapping)

  private lazy val packageMapping = new Mapping[Packaging] {
    lazy val underlying: Mapping[Packaging] = mapping(
      "isLiable" -> mandatoryBoolean,
      "ownBrands" -> boolean,
      "customers" -> boolean
    )(Packaging.apply)(Packaging.unapply)

    override def bind(data: Map[String, String]): Either[Seq[FormError], Packaging] = {
      underlying.bind(data) match {
        case Left(errs) => Left(errs)
        case Right(p) if p.isPackager && !(p.packagesOwnBrand || p.packagesCustomerBrands) =>
          Left(Seq(FormError("manufactureForCheckboxes", "error.packaging.none-selected")))
        case Right(p) => Right(p)
      }
    }

    override def unbind(value: Packaging): Map[String, String] = underlying.unbind(value)

    override def unbindAndValidate(value: Packaging): (Map[String, String], Seq[FormError]) = underlying.unbindAndValidate(value)

    //not required
    override val key: String = ""
    override val mappings: Seq[Mapping[_]] = Nil
    override val constraints: Seq[Constraint[Packaging]] = Nil
    override def withPrefix(prefix: String): Mapping[Packaging] = this
    override def verifying(constraints: Constraint[Packaging]*): Mapping[Packaging] = this
  }
}
