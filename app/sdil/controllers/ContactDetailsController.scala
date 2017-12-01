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

import javax.inject.Inject

import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc.{Action, AnyContent}
import sdil.config.AppConfig
import sdil.models.ContactDetails
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import views.html.softdrinksindustrylevy.register
import sdil.models.sdilmodels._
import scala.concurrent.Future

class ContactDetailsController @Inject()(val messagesApi: MessagesApi, cache: SessionCache)(implicit config: AppConfig)
  extends FrontendController with I18nSupport {

  def displayContactDetails: Action[AnyContent] = Action.async { implicit request =>
    Future successful Ok(register.contact_details(contactForm))
  }

  def submitContactDetails: Action[AnyContent] = Action.async { implicit request =>
    contactForm.bindFromRequest().fold(
      formWithErrors => Future successful BadRequest(register.contact_details(formWithErrors)),
      d => cache.cache("contact-details", d) map { _ =>
        Redirect(routes.SDILController.displayDeclaration())
      })
  }

  private lazy val contactForm = Form(
    mapping(
      "fullName" -> text.verifying(Messages("error.full-name.invalid"), _.nonEmpty),
      "position" -> text.verifying(Messages("error.position.invalid"), _.nonEmpty),
      "phoneNumber" -> text.verifying(Messages("error.phone-number.invalid"), _.length > 10),
      "email" -> email)(ContactDetails.apply)(ContactDetails.unapply))
}
