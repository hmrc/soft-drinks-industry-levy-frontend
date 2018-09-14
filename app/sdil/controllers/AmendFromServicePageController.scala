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

import enumeratum.{Enum, EnumEntry}
import ltbs.play.scaffold.SdilComponents._
import play.api.i18n.{Messages, MessagesApi}
import play.api.mvc.{Action, AnyContent, Result}
import sdil.actions.RegisteredAction
import sdil.config.AppConfig
import sdil.connectors.SoftDrinksIndustryLevyConnector
import sdil.forms.FormHelpers
import sdil.models.retrieved.RetrievedSubscription
import sdil.models.variations.{Convert, VariationData}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.ShortLivedHttpCaching
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import uk.gov.hmrc.uniform.webmonad.{WebMonad, clear, getPath}
import views.html.uniform
import cats.implicits._
import sdil.models.{Address, Producer, SmallProducer}
import sdil.uniform.SaveForLaterPersistence
import ltbs.play.scaffold.GdsComponents._
import play.api.data.Forms.{mapping, nonEmptyText, optional, text}
import play.api.data.Mapping
import play.twirl.api.Html
import uk.gov.hmrc.uniform.HtmlShow
import uk.gov.hmrc.domain.Modulus23Check
import scala.concurrent.duration._

import scala.concurrent.{Await, ExecutionContext, Future}

class AmendFromServicePageController(
  val messagesApi: MessagesApi,
   sdilConnector: SoftDrinksIndustryLevyConnector,
   registeredAction: RegisteredAction,
   cache: ShortLivedHttpCaching
  )(implicit
    val config: AppConfig,
    val ec: ExecutionContext
  ) extends SdilWMController with FrontendController with FormHelpers {

  implicit val smallProducerHtml: HtmlShow[SmallProducer] =
    HtmlShow.instance { producer =>
      Html(producer.alias.map { x =>
        "<h3>" ++ Messages("small-producer-details.name", x) ++"<br/>"
      }.getOrElse(
        "<h3>"
      )
        ++ Messages("small-producer-details.refNumber", producer.sdilRef) ++ "</h3>"
        ++ "<br/>"
        ++ Messages("small-producer-details.lowBand", f"${producer.litreage._1}%,d")
        ++ "<br/>"
        ++ Messages("small-producer-details.highBand", f"${producer.litreage._2}%,d")
      )
    }

  def isSmallProducer(sdilRef: String)(implicit hc: HeaderCarrier): Future[Boolean] =
    sdilConnector.retrieveSubscription(sdilRef).flatMap {
      case Some(x) => x.activity.smallProducer
      case None    => false
    }


  implicit def smallProducer(origSdilRef: String)(implicit hc: HeaderCarrier): Mapping[SmallProducer] = mapping(
    "alias" -> optional(text),
    "sdilRef" -> nonEmptyText
      .verifying(
        "error.sdilref.invalid", x => {
          x.isEmpty ||
            (x.matches("^X[A-Z]SDIL000[0-9]{6}$") &&
              isCheckCorrect(x, 1) &&
              Await.result(isSmallProducer(x), 20.seconds)) &&
              x != origSdilRef
        }),
    "lower"   -> litreage,
    "higher"  -> litreage
  ){
    (alias, ref,l,h) => SmallProducer(alias, ref, (l,h))
  }{
    case SmallProducer(alias, ref, (l,h)) => (alias, ref,l,h).some
  }
  private def program(
                       subscription: RetrievedSubscription,
                       sdilRef: String
                     )(implicit hc: HeaderCarrier): WebMonad[Result] = {
    val addr = Address.fromUkAddress(subscription.address)
    val u = uniform.fragments.update_business_addresses(subscription, addr)
    for {
      askSmallProd   <- ask(bool, "exemptions-for-small-producers")
      firstSmallProd <- ask(smallProducer(sdilRef), "first-small-producer-details") when askSmallProd
      smallProds     <- manyT("small-producer-details",
        {ask(smallProducer(sdilRef), _)},
        min = 1,
        default = firstSmallProd.fold(List.empty[SmallProducer])(x => List(x)),
        editSingleForm = Some((smallProducer(sdilRef), smallProducerForm))
      ) when askSmallProd
    } yield {
      ???
    }
  }


//    def index(id: String): Action[AnyContent] = registeredAction.async { implicit request =>
//      val sdilRef = request.sdilEnrolment.value
//      val persistence = SaveForLaterPersistence("variations", sdilRef, cache)
//      sdilConnector.retrieveSubscription(sdilRef) flatMap {
//        case Some(s) =>
//          runInner(request)(program(s, sdilRef))(id)(persistence.dataGet, persistence.dataPut)
//        case None => NotFound("").pure[Future]
//      }
//    }


  }
