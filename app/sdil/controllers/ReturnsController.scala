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

import cats.implicits._
import java.time.LocalDate
import ltbs.play.scaffold._
import play.api.i18n.MessagesApi
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.mvc._
import play.twirl.api.Html

import scala.concurrent.{ExecutionContext, Future}
import sdil.actions.RegisteredAction
import sdil.config._
import sdil.connectors.SoftDrinksIndustryLevyConnector
import sdil.models.backend.{ Contact, Site, UkAddress }
import sdil.models.retrieved.{ RetrievedActivity, RetrievedSubscription }
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import webmonad._

import scala.collection.mutable.{Map => MMap}
import play.api.data.Forms._
import views.html.gdspages
import enumeratum._
import sdil.models._

class ReturnsController (
  val messagesApi: MessagesApi,
  sdilConnector: SoftDrinksIndustryLevyConnector,
  registeredAction: RegisteredAction,
  keystore: SessionCache
)(implicit
  val config: AppConfig,
  val ec: ExecutionContext
) extends SdilWMController with FrontendController {


  implicit val siteHtml: HtmlShow[Address] =
    HtmlShow.instance { address =>
      val lines = address.nonEmptyLines.mkString("<br />")
      Html(s"<div>$lines</div>")
    }

  protected def askContactDetails(
    id: String, default: Option[ContactDetails]
  ): WebMonad[ContactDetails] = {
    val contactMapping = mapping(
      "fullName" -> nonEmptyText,
      "position" -> nonEmptyText,
      "phoneNumber" -> nonEmptyText,
      "email" -> nonEmptyText
    )(ContactDetails.apply)(ContactDetails.unapply)


    formPage(id)(contactMapping, default) { (path, b, r) =>
      implicit val request: Request[AnyContent] = r
      gdspages.contactdetails(id, b, path)
    }

  }

  // Nasty hack to prevent me from having to either create
  // good function signatures or put '.some' after all the
  // defaults
  private implicit def toSome[A](in: A): Option[A] = in.some

  private def program(subscription: RetrievedSubscription): WebMonad[Result] = for {
    produced <- askLitreage("returnsProduced")
    exit <- journeyEnd("returnsDone")
  } yield {
    exit
  }

  def index(id: String): Action[AnyContent] = registeredAction.async { implicit request =>
    sdilConnector.retrieveSubscription(request.sdilEnrolment.value) flatMap {
      case Some(s) => runInner(request)(program(s))(id)(dataGet,dataPut)
      case None => NotFound("").pure[Future]
    }
  }

}
