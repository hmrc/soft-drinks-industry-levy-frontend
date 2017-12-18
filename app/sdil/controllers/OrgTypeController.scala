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

import play.api.data.Forms._
import play.api.data.{Form, Mapping}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent}
import sdil.actions.FormAction
import sdil.config.AppConfig
import sdil.forms.FormHelpers
import sdil.models.{OrgTypePage, RegistrationFormData}
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import uk.gov.hmrc.play.views.html.helpers.form
import views.html.softdrinksindustrylevy.register

import scala.concurrent.Future


class OrgTypeController(val messagesApi: MessagesApi, cache: SessionCache, formAction: FormAction)(implicit config: AppConfig)
  extends FrontendController with I18nSupport {

  import OrgTypeController.form

  def displayOrgType(): Action[AnyContent] = formAction.async { implicit request =>
    OrgTypePage.expectedPage(request.formData) match {
      case OrgTypePage => Ok(register.organisation_type(form))
      case otherPage => Redirect(otherPage.show)
    }
  }

  def submitOrgType(): Action[AnyContent] = formAction.async { implicit request =>
    form.bindFromRequest().fold(
      errors => Future.successful(BadRequest(register.organisation_type(errors))),
      orgType =>
        cache.cache("formData", request.formData.copy(orgType = Some(orgType))) map { _ =>
          if (orgType == "partnership") Redirect("noPartnerships")
          else
            Redirect(routes.PackageController.displayPackage())
        }
    )
  }
}

object OrgTypeController extends FormHelpers {

  val form: Form[String] = Form(single(
    "orgType" -> oneOf(Seq("limitedCompany", "limitedLiabilityPartnership", "partnership", "soleTrader", "unincorporatedBody"), "error.radio-form.choose-one-option")
  ))
}
