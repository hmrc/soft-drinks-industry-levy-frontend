/*
 * Copyright 2022 HM Revenue & Customs
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

import cats.data.OptionT
import cats.implicits._
import play.api.data.{Form, Mapping}
import play.api.data.Forms.{mapping, text}
import play.api.data.validation.{Constraint, Invalid, Valid}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{MessagesControllerComponents, Result}
import sdil.actions.{AuthorisedAction, AuthorisedRequest}
import sdil.config.{AppConfig, RegistrationFormDataCache}
import sdil.connectors.SoftDrinksIndustryLevyConnector
import sdil.forms.FormHelpers
import sdil.models.{Address, Identification, RegistrationFormData}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.Views
import sdil.uniform.SdilComponents.postcode

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class IdentifyController @Inject()(
  mcc: MessagesControllerComponents,
  cache: RegistrationFormDataCache,
  authorisedAction: AuthorisedAction,
  softDrinksIndustryLevyConnector: SoftDrinksIndustryLevyConnector,
  views: Views)(implicit config: AppConfig, ec: ExecutionContext)
    extends FrontendController(mcc) with I18nSupport {

  import IdentifyController.form

  def show = authorisedAction { implicit request =>
    Ok(views.identify(form))
  }

  def start = authorisedAction.async { implicit request =>
    restoreSession
      .orElse(retrieveRosmData)
      .getOrElse(Redirect(routes.IdentifyController.show))
  }

  private def restoreSession(implicit request: AuthorisedRequest[_]): OptionT[Future, Result] =
    OptionT(cache.get(request.internalId) map {
      case Some(_) => Some(Redirect(routes.VerifyController.show()))
      case None    => None
    })

  private def retrieveRosmData(implicit request: AuthorisedRequest[_]): OptionT[Future, Result] =
    for {
      utr      <- OptionT.fromOption[Future](request.utr)
      rosmData <- OptionT(softDrinksIndustryLevyConnector.getRosmRegistration(utr))
      _        <- OptionT.liftF(cache.cache(request.internalId, RegistrationFormData(rosmData, utr)))
    } yield {
      Redirect(routes.VerifyController.show())
    }

  def submit = authorisedAction.async { implicit request =>
    form
      .bindFromRequest()
      .fold(
        errors => BadRequest(views.identify(errors)),
        identification =>
          softDrinksIndustryLevyConnector.getRosmRegistration(identification.utr) flatMap {
            case Some(reg) if postcodesMatch(reg.address, identification) =>
              cache.cache(request.internalId, RegistrationFormData(reg, identification.utr)) map { _ =>
                Redirect(routes.VerifyController.show())
              }
            case _ => BadRequest(views.identify(form.fill(identification).withError("utr", "utr.no-record")))
        }
      )
  }

  private def postcodesMatch(rosmAddress: Address, identification: Identification) =
    rosmAddress.postcode.replaceAll(" ", "").equalsIgnoreCase(identification.postcode.replaceAll(" ", ""))
}

object IdentifyController extends FormHelpers {

  val form: Form[Identification] = Form(
    mapping(
      "utr" -> text.verifying(Constraint { x: String =>
        x match {
          case ""                            => Invalid("utr.required")
          case utr if utr.exists(!_.isDigit) => Invalid("utr.invalid")
          case utr if utr.length != 10       => Invalid("utr.length")
          case _                             => Valid
        }
      }),
      "postcode" -> postcode
    )(Identification.apply)(Identification.unapply)
  )
}
