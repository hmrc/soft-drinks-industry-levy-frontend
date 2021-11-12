/*
 * Copyright 2021 HM Revenue & Customs
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

import scala.language.higherKinds

import ltbs.uniform._
import ltbs.uniform.interpreters.playframework._
import play.api.i18n._
import play.api.mvc._
import play.twirl.api.Html
import sdil.config.AppConfig
import sdil.models.backend.Site
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import scala.concurrent.{ExecutionContext, Future}

class RegistrationControllerNew(
  mcc: MessagesControllerComponents,
  val config: AppConfig,
  val ufViews: views.uniform.Uniform,
  implicit val ec: ExecutionContext
) extends FrontendController(mcc) with I18nSupport with HmrcPlayInterpreter {

  def index(id: String): Action[AnyContent] = Action.async { implicit request: Request[AnyContent] =>
    implicit lazy val persistence: PersistenceEngine[Request[AnyContent]] =
      SessionPersistence("registration")

    interpret(RegistrationControllerNew.journey).run(id) { _ =>
      Future.successful(Ok("hiya"))
    }
  }

}

object RegistrationControllerNew {

  import ltbs.uniform._

  val journey = for {
    _ <- ask[java.time.LocalDate]("firstpage")
    _ <- askList[Site]("sites") {
          case (index: Option[Int], existing: List[Site]) =>
            ask[Site]("site", default = index.map(existing))
        }
    _ <- ask[Either[Boolean, Int]]("thirdpage")
    _ <- ask[Int]("simple")
  } yield ()

}
