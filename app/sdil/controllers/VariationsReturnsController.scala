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

import scala.language.{higherKinds, postfixOps}
import cats.implicits._
import ltbs.uniform._
import play.api.Logger
import play.api.i18n.I18nSupport
import play.api.mvc._
import play.twirl.api.Html

import scala.concurrent._
import sdil.actions._
import sdil.config._
import sdil.connectors.SoftDrinksIndustryLevyConnector
import sdil.models._
import sdil.models.variations._
import sdil.uniform.SaveForLaterPersistenceNew
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import sdil.journeys.{VariationsJourney, VariationsReturnsJourney}
import scala.concurrent.{ExecutionContext, Future}

import javax.inject.Inject

class VariationsReturnsController @Inject()(
  mcc: MessagesControllerComponents,
  val config: AppConfig,
  val ufViews: views.uniform.Uniform,
  registeredAction: RegisteredAction,
  sdilConnector: SoftDrinksIndustryLevyConnector,
  regCache: RegistrationFormDataCache,
  returnsVariationsCache: ReturnVariationFormDataCache,
)(implicit ec: ExecutionContext)
    extends FrontendController(mcc) with I18nSupport with HmrcPlayInterpreter {

  override def defaultBackLink = "/soft-drinks-industry-levy"

  override def blockCollections[X[_] <: Traversable[_], A]: common.web.WebAsk[Html, X[A]] = ???

  override def blockCollections2[X[_] <: Traversable[_], A]: common.web.WebAsk[Html, X[A]] = ???

  val logger: Logger = Logger(this.getClass())

  implicit lazy val codecOptRet: common.web.Codec[Option[SdilReturn]] = {
    import java.time.LocalDateTime
    implicit def c: common.web.Codec[LocalDateTime] =
      stringField.simap { s =>
        Either
          .catchOnly[java.time.format.DateTimeParseException](
            LocalDateTime.parse(s)
          )
          .leftMap(_ => ErrorTree.oneErr(ErrorMsg("not-a-date")))
      }(_.toString)

    common.web.InferCodec.gen[Option[SdilReturn]]
  }

  def journey(id: String): Action[AnyContent] = registeredAction.async { implicit request =>
    implicit lazy val persistence = {
      SaveForLaterPersistenceNew[RegisteredRequest[AnyContent]](_.sdilEnrolment.value)("variations", regCache)
    }

    val sdilRef = request.sdilEnrolment.value
    val emptyReturn = SdilReturn((0, 0), (0, 0), List.empty, (0, 0), (0, 0), (0, 0), (0, 0))

    (for {
      subscription <- sdilConnector.retrieveSubscription(sdilRef).map { _.get }
      base = RegistrationVariationData(subscription)
      variableReturns <- sdilConnector.returns_variable(base.original.utr)

      r <-  {
            def getReturn(period: ReturnPeriod): Future[Option[SdilReturn]] =
              sdilConnector.returns_get(subscription.utr, period)
            def checkSmallProducerStatus(sdilRef: String, period: ReturnPeriod): Future[Option[Boolean]] =
              sdilConnector.checkSmallProducerStatus(sdilRef, period)
            def submitAdjustment(rvd: ReturnVariationData) =
              sdilConnector.returns_vary(sdilRef, rvd)

            interpret(
              VariationsReturnsJourney
                .journey(
                  id,
                  subscription,
                  Some(emptyReturn),
                  sdilRef,
                  variableReturns,
                  sdilConnector,
                  checkSmallProducerStatus,
                  getReturn,
                  config
                )
            ).run(id, purgeStateUponCompletion = true, config = cyajourneyConfig) {
              case ret =>
                submitAdjustment(ret).flatMap { _ =>
                  returnsVariationsCache.cache(sdilRef, ret).flatMap { _ =>
                    logger.info("adjustment of Return is complete")
                    Redirect(routes.VariationsController.showVariationsComplete())
                  }
                }
              case _ =>
                logger.info("failed to find return")
                Redirect(routes.ServicePageController.show)
            }
    } yield r) recoverWith {
      case t: Throwable => {
        logger.error(s"Exception occurred while retrieving pendingReturns for sdilRef =  $sdilRef", t)
        Redirect(routes.ServicePageController.show).pure[Future]
      }
    }
  }
}
