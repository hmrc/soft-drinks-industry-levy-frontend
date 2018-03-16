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

import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent}
import sdil.actions.FormAction
import sdil.config.{AppConfig, FormDataCache}
import sdil.connectors.{AnalyticsRequest, Event, GaConnector}
import sdil.forms.FormHelpers
import sdil.models.OrgTypePage
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import views.html.softdrinksindustrylevy.register

import scala.concurrent.Future

class OrganisationTypeController(val messagesApi: MessagesApi, cache: FormDataCache, formAction: FormAction,
                                 gaConnector: GaConnector)
                                (implicit config: AppConfig)
  extends FrontendController with I18nSupport {

  import OrganisationTypeController.form

  def show(): Action[AnyContent] = formAction.async { implicit request =>
    val hasCTEnrolment = request.enrolments.getEnrolment("IR-CT").isDefined
    val f = request.formData.organisationType.fold(form(hasCTEnrolment))(form(hasCTEnrolment).fill)

    OrgTypePage.expectedPage(request.formData) match {
      case OrgTypePage => Ok(register.organisation_type(f, hasCTEnrolment))
      case otherPage => Redirect(otherPage.show)
    }
  }

  def submit(): Action[AnyContent] = formAction.async { implicit request =>
    val hasCTEnrolment = request.enrolments.getEnrolment("IR-CT").isDefined

    form(hasCTEnrolment).bindFromRequest().fold(
      errors => Future.successful(BadRequest(register.organisation_type(errors, hasCTEnrolment))),
      orgType => {
        val event = Event("orgType", "selectOrg", orgType)
        gaConnector.sendEvent(AnalyticsRequest(request.cookies.get("_ga").map(_.value).getOrElse(""), Seq(event))) flatMap {
          _ =>
            val formData = request.formData.copy(organisationType = Some(orgType))
            cache.cache(request.internalId, formData) map { fd =>
              if (orgType == "partnership") Redirect(routes.OrganisationTypeController.displayPartnerships())
              else
                Redirect(OrgTypePage.nextPage(formData).show)
            }
        }
      }
    )
  }

  def displayPartnerships(): Action[AnyContent] = formAction.async { implicit request =>
    request.formData.organisationType match {
      case Some("partnership") => Ok(register.partnerships())
      case _ => Redirect(routes.OrganisationTypeController.show())
    }
  }
}

object OrganisationTypeController extends FormHelpers {

  def form(hasCTEnrolment: Boolean): Form[String] = Form(single(
    "orgType" -> oneOf(options(hasCTEnrolment), "error.radio-form.choose-option")
  ))

  private def options(hasCTEnrolment: Boolean): Seq[String] = {
    val soleTrader: Seq[String] = if (hasCTEnrolment) Nil else Seq("soleTrader")

    Seq(
      "limitedCompany",
      "limitedLiabilityPartnership",
      "partnership",
      "unincorporatedBody"
    ) ++ soleTrader
  }
}
