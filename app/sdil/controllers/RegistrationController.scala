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

import cats.data.OptionT
import ltbs.play.scaffold.webmonad.WebMonad
import org.omg.CosNaming.NamingContextPackage.NotFound
import cats.implicits._
import ltbs.play.scaffold.GdsComponents.bool
import ltbs.play.scaffold.SdilComponents.warehouseSiteForm

import scala.concurrent.{ExecutionContext, Future}
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, AnyContent, Result, Results}
import sdil.actions.{AuthorisedAction, AuthorisedRequest, RegisteredAction}
import sdil.config.{AppConfig, RegistrationFormDataCache}
import sdil.connectors.SoftDrinksIndustryLevyConnector
import sdil.models.{Producer, RegistrationFormData}
import sdil.uniform.SaveForLaterPersistence
import uk.gov.hmrc.http.cache.client.{SessionCache, ShortLivedHttpCaching}
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import views.html.softdrinksindustrylevy.register
import views.html.softdrinksindustrylevy.register.partnerships
import views.html.uniform
import ltbs.play.scaffold.webmonad._
import ltbs.play.scaffold.GdsComponents._
import ltbs.play.scaffold.SdilComponents._


class RegistrationController(val messagesApi: MessagesApi,
                             authorisedAction: AuthorisedAction,
                             sdilConnector: SoftDrinksIndustryLevyConnector,
                             registeredAction: RegisteredAction,
                             cache: RegistrationFormDataCache
                            )(implicit
                              val config: AppConfig,
                              val ec: ExecutionContext
                            ) extends SdilWMController with FrontendController {

  val orgTypes: List[String] = List(
    "limitedCompany",
    "limitedLiabilityPartnership",
    "partnership",
    "unincorporatedBody"
  )
  def orgTypes(hasCTEnrolment: Boolean): List[String] = {
    val soleTrader: Seq[String] = if (hasCTEnrolment) Nil else Seq("soleTrader")
    orgTypes ++ soleTrader
  }


  def index: Action[AnyContent] = authorisedAction.async { implicit request =>
    val persistence = SaveForLaterPersistence("registration", request.internalId, cache.shortLiveCache)
    val fd = cache.shortLiveCache.fetch(request.internalId)
    val hasCTEnrolment = request.enrolments.getEnrolment("IR-CT").isDefined
    //    runInner(request)(program(fd: RegistrationFormData)(persistence.dataGet,persistence.dataPut)
      NotFound("").pure[Future]
  }


  private def program(fd: RegistrationFormData, request: AuthorisedRequest[AnyContent]): WebMonad[Result] = {
    val hasCTEnrolment = request.enrolments.getEnrolment("IR-CT").isDefined
    val soleTrader = if (hasCTEnrolment) Nil else Seq("soleTrader")

    for {
      orgType       <- askOneOf("organisationType", orgTypes ++ soleTrader)
      _             <- if (orgType.value.pure[String] === "partnership") { Ok(partnerships()) } else (()).pure[WebMonad]
      packLarge     <- askOption(bool, "packLarge")
      useCopacker   <- ask(bool,"useCopacker") when packLarge.contains(false)

    } yield NotFound("").pure[Future] // TODO fix
  }


//    for {
//      _ <- Nil
//    } yield NotFound("").pure[Future]
//


//  private def program(period: ReturnPeriod, subscription: Subscription, sdilRef: String)(implicit hc: HeaderCarrier): WebMonad[Result] = for {
//    start <- identify()
//    verify <- verify()
//
//    //    sdilReturn     <- askReturn
//    //    broughtForward <- BigDecimal("0").pure[WebMonad]
//    //    _              <- checkYourAnswers("check-your-answers", sdilReturn, broughtForward)
//    //    _              <- cachedFuture(s"return-${period.count}")(
//    //      sdilConnector.returns(subscription.utr, period) = sdilReturn)
//    end <- clear >> confirmationPage("return-sent", period, subscription, sdilReturn, broughtForward, sdilRef)
//  } yield end

}
