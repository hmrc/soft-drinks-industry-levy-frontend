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

import java.time.LocalDate

import cats.implicits._
import ltbs.play.scaffold.GdsComponents.litreagePair
import ltbs.play.scaffold.SdilComponents.{OrganisationType, OrganisationTypeSoleless, ProducerType}
import scala.language.higherKinds

import ltbs.uniform._
import ltbs.uniform.common.web.{PageIn, PageOut, WebInteraction, WebMonad, WebTell}
import ltbs.uniform.interpreters.playframework._
import ltbs.uniform.validation.Rule
import play.api.i18n._
import play.api.mvc.{request, _}
import play.twirl.api.Html
import sdil.config.AppConfig
import sdil.models.backend.Site
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import uk.gov.hmrc.uniform.HtmlShow

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
    val hasCTEnrolment = true //request.enrolments.getEnrolment("IR-CT").isDefined TODO

    interpret(RegistrationControllerNew.journey(hasCTEnrolment)).run(id) { _ =>
      Future.successful(Ok("hiya"))
    }
  }

}

object RegistrationControllerNew {

  def journey(hasCTEnrolment: Boolean) =
    for {

      orgType <- if (hasCTEnrolment) ask[OrganisationTypeSoleless]("organisation-type")
                else ask[OrganisationType]("organisation-type")
      _ <- end("partnerships") when (orgType.entryName == OrganisationType.partnership.entryName)
      packLarge <- ask[ProducerType]("producer") map {
                    case ProducerType.Large => Some(true)
                    case ProducerType.Small => Some(false)
                    case _                  => None
                  }
      useCopacker <- ask[Boolean]("copacked") when packLarge.contains(false)
      packageOwn  <- ask[Some[(Long, Long)]]("package-own-uk") when packLarge.nonEmpty
      copacks     <- ask[Some[(Long, Long)]]("package-copack")
      imports     <- ask[Some[(Long, Long)]]("import")
      noUkActivity = (copacks, imports).isEmpty
      smallProducerWithNoCopacker = packLarge.forall(_ == false) && useCopacker.forall(_ == false)
      _ <- end("do-not-register") when (noUkActivity && smallProducerWithNoCopacker)
      isVoluntary = packLarge.contains(false) && useCopacker.contains(true) && (copacks, imports).isEmpty
      regDate <- ask[LocalDate]("start-date") unless isVoluntary
      askPackingSites = (packLarge.contains(true) && packageOwn.flatten.nonEmpty) || copacks.isDefined
      useBusinessAddress <- ask[Boolean]("pack-at-business-address") when askPackingSites

      _ <- ask[LocalDate]("firstpage")
      _ <- askList[Site]("sites") {
            case (index: Option[Int], existing: List[Site]) =>
              ask[Site]("site", default = index.map(existing))
          }
//      _                  <- interact[SmallProducer]("sndpage", Html("test"))
      _ <- ask[Either[Boolean, Int]]("thirdpage")
      _ <- ask[Int]("simple")
    } yield ()

}
