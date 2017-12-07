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

import play.api.data.Forms.{boolean, mapping}
import play.api.data.validation.Constraint
import play.api.data.{Form, FormError, Mapping}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent}
import sdil.config.AppConfig
import sdil.forms.FormHelpers
import sdil.models.Packaging
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import views.html.softdrinksindustrylevy.register

import scala.concurrent.Future

class PackageController(val messagesApi: MessagesApi, cache: SessionCache)(implicit config: AppConfig) extends FrontendController with I18nSupport {
  
  import PackageController._

  def displayPackage(): Action[AnyContent] = Action.async { implicit request =>
    Future successful Ok(register.packagePage(form))
  }

  def submitPackage(): Action[AnyContent] = Action.async { implicit request =>
    form.bindFromRequest.fold(
      formWithErrors => BadRequest(register.packagePage(formWithErrors)),
      validFormData => cache.cache("packaging", validFormData) map { _ =>
        validFormData match {
          case Packaging(_, true, _) => Redirect(routes.LitreageController.show("packageOwn"))
          case Packaging(_, _, true) => Redirect(routes.LitreageController.show("packageCopack"))
          case _ => Redirect(routes.RadioFormController.display(page = "package-copack-small", trueLink = "packageCopackSmallVol", falseLink = "copacked"))
        }
      }
    )
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
        case Right(p) if p.isLiable && !(p.ownBrands || p.customers) =>
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
