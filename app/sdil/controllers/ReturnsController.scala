/*
 * Copyright 2023 HM Revenue & Customs
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
import play.api.Logger
import play.api.i18n._
import play.api.mvc._
import play.twirl.api.Html
import sdil.actions.{RegisteredAction, RegisteredRequest}
import sdil.config.{AppConfig, RegistrationFormDataCache, ReturnsFormDataCache}
import sdil.connectors.SoftDrinksIndustryLevyConnector
import sdil.journeys.ReturnsJourney
import sdil.models._
import sdil.models.variations.ReturnVariationData
import sdil.uniform.SaveForLaterPersistenceNew
import sdil.utility.stringToFormatter
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.main_template

import java.time.{LocalDate, LocalTime, ZoneId}
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

class ReturnsController @Inject()(
  mcc: MessagesControllerComponents,
  val config: AppConfig,
  val ufViews: views.uniform.Uniform,
  registeredAction: RegisteredAction,
  sdilConnector: SoftDrinksIndustryLevyConnector,
  cache: RegistrationFormDataCache,
  returnsCache: ReturnsFormDataCache
)(implicit ec: ExecutionContext)
    extends FrontendController(mcc) with I18nSupport with HmrcPlayInterpreter {
  val logger: Logger = Logger(this.getClass())
  override def defaultBackLink = "/soft-drinks-industry-levy"

  def index(year: Int, quarter: Int, nilReturn: Boolean, id: String): Action[AnyContent] = registeredAction.async {
    implicit request =>
      println(Console.YELLOW + "reaching index of returns controller " + Console.WHITE)
      redirectSessionToNewReturnsURL(ReturnPeriod(year, quarter), nilReturn)
      implicit lazy val persistence =
        SaveForLaterPersistenceNew[RegisteredRequest[AnyContent]](_.sdilEnrolment.value)(
          s"returns-$year-$quarter",
          cache
        )

      val sdilRef = request.sdilEnrolment.value
      val period = ReturnPeriod(year, quarter)
      val emptyReturn = SdilReturn((0, 0), (0, 0), List.empty, (0, 0), (0, 0), (0, 0), (0, 0))

      (for {
        subscription   <- sdilConnector.retrieveSubscription(sdilRef).map { _.get }
        pendingReturns <- sdilConnector.returns_pending(subscription.utr)
        broughtForward <- if (config.balanceAllEnabled)
                           sdilConnector.balanceHistory(sdilRef, withAssessment = false).map { x =>
                             extractTotal(listItemsWithTotal(x))
                           } else sdilConnector.balance(sdilRef, withAssessment = false)
        isSmallProd <- sdilConnector.checkSmallProducerStatus(sdilRef, period).flatMap {
                        case Some(x) =>
                          x // the given sdilRef matches a customer that was a small producer at some point in the quarter
                        case None => false
                      }

        r <- if (pendingReturns.contains(period)) {
              def submitReturn(sdilReturn: SdilReturn): Future[Unit] =
                sdilConnector.returns_update(subscription.utr, period, sdilReturn)
              def checkSmallProducerStatus(sdilRef: String, period: ReturnPeriod): Future[Option[Boolean]] =
                sdilConnector.checkSmallProducerStatus(sdilRef, period)
              def submitReturnVariation(rvd: ReturnsVariation): Future[Unit] =
                sdilConnector.returns_variation(rvd, sdilRef)

              if (nilReturn)
                interpret(
                  ReturnsJourney
                    .cyaJourney(
                      period,
                      emptyReturn,
                      subscription,
                      submitReturnVariation,
                      broughtForward,
                      isSmallProd
                    )
                ).run(id, purgeStateUponCompletion = true, config = journeyConfig) { ret =>
                  submitReturn(ret._1).flatMap { _ =>
                    returnsCache.cache(request.sdilEnrolment.value, ReturnsFormData(ret._1, ret._2)).flatMap { _ =>
                      Redirect(routes.ReturnsController.showReturnComplete(year, quarter))
                    }
                  }
                } else
                interpret(
                  ReturnsJourney
                    .journey(
                      id,
                      period,
                      None,
                      subscription,
                      checkSmallProducerStatus,
                      submitReturnVariation,
                      broughtForward,
                      isSmallProd
                    )
                ).run(id, purgeStateUponCompletion = true, config = journeyConfig) { ret =>
                  submitReturn(ret._1).flatMap { _ =>
                    returnsCache.cache(request.sdilEnrolment.value, ReturnsFormData(ret._1, ret._2)).flatMap { _ =>
                      Redirect(routes.ReturnsController.showReturnComplete(year, quarter))
                    }
                  }
                }

            } else
              Redirect(routes.ServicePageController.show).pure[Future]
      } yield r) recoverWith {
        case t: Throwable => {
          logger.error(s"Exception occurred while retrieving pendingReturns for sdilRef =  $sdilRef", t)
          Redirect(routes.ServicePageController.show).pure[Future]
        }
      }
  }

  private def redirectSessionToNewReturnsURL(returnPeriod: ReturnPeriod, nilReturn: Boolean): OptionT[Future, Result] =
    OptionT(Future {
      if (config.redirectToNewReturnsEnabled) {
        val url = config.startReturnUrl(returnPeriod.year, returnPeriod.quarter, nilReturn)
        Some(Redirect(url))
      } else {
        None
      }
    })

  def showReturnComplete(year: Int, quarter: Int): Action[AnyContent] = registeredAction.async { implicit request =>
    val sdilRef = request.sdilEnrolment.value
    val period = ReturnPeriod(year, quarter)

    for {
      subscription <- sdilConnector.retrieveSubscription(sdilRef).map { _.get }
      isSmallProd <- sdilConnector.checkSmallProducerStatus(sdilRef, period).flatMap {
                      case Some(x) =>
                        x // the given sdilRef matches a customer that was a small producer at some point in the quarter
                      case None => false
                    }
      broughtForward <- if (config.balanceAllEnabled)
                         sdilConnector.balanceHistory(sdilRef, withAssessment = false).map { x =>
                           extractTotal(listItemsWithTotal(x))
                         } else sdilConnector.balance(sdilRef, withAssessment = false)
      cache <- returnsCache.get(sdilRef)
    } yield {
      cache match {
        case Some(rd) =>
          val now = LocalDate.now
          val returnDate = Messages(
            "return-sent.returnsDoneMessage",
            period.start.format("MMMM"),
            period.end.format("MMMM"),
            period.start.getYear.toString,
            subscription.orgName,
            LocalTime.now(ZoneId.of("Europe/London")).format(DateTimeFormatter.ofPattern("h:mma")).toLowerCase,
            now.format("dd MMMM yyyy")
          )

          val data = ReturnsJourney.returnAmount(rd.sdilReturn, isSmallProd)
          val subtotal = ReturnsJourney.calculateSubtotal(data)
          val total = subtotal - broughtForward

          val formatTotal =
            if (total < 0)
              f"-£${total.abs}%,.2f"
            else
              f"£$total%,.2f"

          val prettyPeriod =
            Messages(
              s"period.check-your-answers",
              period.start.format("MMMM"),
              period.end.format("MMMM yyyy")
            )

          val getTotal =
            if (total <= 0)
              Messages("return-sent.subheading.nil-return")
            else {
              Messages(
                "return-sent.subheading",
                prettyPeriod,
                subscription.orgName
              )
            }

          val whatHappensNext = views.html.uniform.fragments
            .returnsPaymentsBlurb(
              subscription = subscription,
              paymentDate = period,
              sdilRef = sdilRef,
              total = total,
              formattedTotal = formatTotal,
              variation = rd.variation,
              lineItems = data,
              costLower = ReturnsJourney.costLower,
              costHigher = ReturnsJourney.costHigher,
              subtotal = ReturnsJourney.calculateSubtotal(data),
              broughtForward = broughtForward
            )
            .some

          Ok(
            main_template(
              Messages("return-sent.title")
            )(
              views.html.uniform
                .journeyEndNew("return-sent", now, Html(returnDate).some, whatHappensNext, Html(getTotal).some)
            )(implicitly, implicitly, config)
          )

        case None =>
          logger.warn("nothing in ReturnsFormDataCache, redirecting user to ServicePage")
          Redirect(routes.ServicePageController.show)
      }
    }
  }

}
