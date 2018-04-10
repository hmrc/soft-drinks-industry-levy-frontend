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
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent}
import sdil.actions.FormAction
import sdil.config.{AppConfig, RegistrationFormDataCache}
import sdil.forms.FormHelpers
import sdil.models._
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import views.html.softdrinksindustrylevy.register

class ProducerController(val messagesApi: MessagesApi, cache: RegistrationFormDataCache, formAction: FormAction)(implicit config: AppConfig)
  extends FrontendController with I18nSupport {

  import ProducerController._

  def show(): Action[AnyContent] = formAction.async { implicit request =>
    Journey.expectedPage(ProducerPage) match {
      case ProducerPage => Ok(register.produce_worldwide(request.formData.producer.fold(form)(form.fill)))
      case otherPage => Redirect(otherPage.show)
    }
  }

  def submit(): Action[AnyContent] = formAction.async { implicit request =>
    form.bindFromRequest.fold(
      formWithErrors => BadRequest(register.produce_worldwide(formWithErrors)),
      producer => {
        val updated = updateData(request.formData, producer)
        cache.cache(request.internalId, updated) map { _ =>
          Redirect(Journey.nextPage(ProducerPage, updated).show)
        }
      }
    )
  }

  lazy val backLink = routes.OrganisationTypeController.show()

  lazy val submitAction = routes.ProducerController.submit()

  //clear unneeded data from session cache when the user's answers change
  private def updateData(formData: RegistrationFormData, producer: Producer) = producer match {
    case Producer(false, _) =>
      formData.copy(
        producer = Some(Producer(false, None)),
        usesCopacker = None,
        isPackagingForSelf = None,
        volumeForOwnBrand = None
      )
    case Producer(true, Some(true)) =>
      formData.copy(producer = Some(producer), usesCopacker = None)
    case _ => formData.copy(producer = Some(producer))
  }
}

object ProducerController extends FormHelpers {
  import play.api.data.Forms._
  import uk.gov.voa.play.form.ConditionalMappings.mandatoryIfTrue
  val form: Form[Producer] = Form(
    mapping(
    "isProducer" -> mandatoryBoolean,
    "isLarge" -> mandatoryIfTrue("isProducer", mandatoryBoolean)
  )(Producer.apply)(Producer.unapply))

}
