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

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneId}

import play.api.data.Forms._
import play.api.data.{Form, Forms}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent}
import sdil.actions.VariationAction
import sdil.config.AppConfig
import sdil.connectors.SoftDrinksIndustryLevyConnector
import sdil.models.variations.Convert
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import views.html.softdrinksindustrylevy.variations.variations_summary

class VariationsSummaryController(val messagesApi: MessagesApi,
                                  variationAction: VariationAction,
                                  sdilConnector: SoftDrinksIndustryLevyConnector)
                                 (implicit config: AppConfig)
  extends FrontendController with I18nSupport {

  def show: Action[AnyContent] = variationAction { implicit request =>
    Ok(variations_summary(VariationsSummaryController.form, Convert(request.data)))
  }

  def submit: Action[AnyContent] = variationAction.async { implicit request =>
    val variation = Convert(request.data)
    val sdilNumber = request.wrapped.sdilEnrolment.value

    if (variation.isEmpty) {
      Redirect(routes.VariationsSummaryController.confirmation())
    } else if(!(request.data.isLiable || request.data.isVoluntary)) {
      VariationsSummaryController.form.bindFromRequest().fold(
        errors => BadRequest(variations_summary(errors, variation)),
        deregReason => sdilConnector.submitVariation(
          variation.copy(deregistrationText = Some(deregReason)),
          sdilNumber
        ) map { _ => Redirect(routes.VariationsSummaryController.confirmation()) }
      )

    } else if (variation.sdilActivity.nonEmpty) {
      VariationsSummaryController.form.bindFromRequest().fold(
        errors => BadRequest(variations_summary(errors, variation)),
        amendReason => sdilConnector.submitVariation(
          variation.copy(sdilActivity = variation.sdilActivity.map(_.copy(reasonForAmendment = Some(amendReason)))),
          sdilNumber
        ) map { _ => Redirect(routes.VariationsSummaryController.confirmation()) }
      )
    } else {
      sdilConnector.submitVariation(variation, sdilNumber) map { _ => Redirect(routes.VariationsSummaryController.confirmation()) }
    }
  }

  def confirmation: Action[AnyContent] = Action.async { implicit request =>
    val ts = LocalDateTime.now(ZoneId.of("Europe/London"))

    lazy val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy")
    lazy val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

    Ok(views.html.softdrinksindustrylevy.variations.confirmation(ts.format(dateFormatter), ts.format(timeFormatter)))
  }
}

object VariationsSummaryController {
  val form: Form[String] = Form(Forms.single("variationReason" -> nonEmptyText))
}
