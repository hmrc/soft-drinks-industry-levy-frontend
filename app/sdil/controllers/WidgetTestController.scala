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

import cats.implicits._
import enumeratum._
import java.time.LocalDate
import ltbs.uniform._, validation._
import ltbs.uniform.common.web._
import ltbs.uniform.interpreters.playframework.SessionPersistence
import play.api.Logger
import play.api.i18n.I18nSupport
import play.api.mvc._
import play.twirl.api.Html
import scala.concurrent._
import sdil.actions._
import sdil.config._
import sdil.connectors.SoftDrinksIndustryLevyConnector
import sdil.models._
import sdil.models.backend.Site
import sdil.models.retrieved.RetrievedSubscription
import sdil.models.variations._
import sdil.uniform._
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import cats.data.Validated

case class Subset[A](options: Set[A]) extends Rule[Set[A]] {
  def apply(values: Set[A]): Validated[ErrorTree, Set[A]] =
    Validated.cond(values.subsetOf(options), values, Rule.error("invalid-option"))
}

object Subset {
  def apply[A](values: A*): Subset[A] = Subset(values.toSet)
}

class WidgetTestController(
  mcc: MessagesControllerComponents,
  val config: AppConfig,
  val ufViews: views.uniform.Uniform
)(implicit ec: ExecutionContext)
    extends FrontendController(mcc) with I18nSupport with HmrcPlayInterpreter {

  override def defaultBackLink = "/soft-drinks-industry-levy"

  val logger: Logger = Logger(this.getClass())

  sealed trait Nat extends EnumEntry
  object Nat extends Enum[Nat] {
    val values = findValues
    case object One extends Nat
    case object Two extends Nat
    case object Three extends Nat
    case object FiveThousandAndSix extends Nat
  }

  case class CYATestThing(
    enums: Set[Nat],
    anInt: Int,
    text: String
  )

  implicit def tellCyaTestThing = new WebTell[Html, CYATestThing] {
    def render(in: CYATestThing, key: List[String], pageIn: PageIn[Html]): Option[Html] = Some {

      views.html.softdrinksindustrylevy.helpers.cya_simple_block(
        key,
        pageIn.breadcrumbs,
        pageIn.messages
      )(
        List("checkboxes-test") -> Html(in.enums.mkString(", ")),
        List("an-int")          -> Html(in.anInt.toString) // we don't know if the question exists in their journey
      )
    }
  }

  def index(id: String): Action[AnyContent] = Action.async { implicit req =>
    implicit val persistence = new SessionPersistence("test")

    val simpleJourney = for {
      ns <- ask[Set[Nat]](
             "checkboxes-test",
             validation = Subset(Nat.One, Nat.Two, Nat.Three) // FiveThousandAndSix shouldn't have a checkbox
           )
      _     <- tell("a-tell", Html("hiya"))
      anInt <- ask[Int]("an-int") when ns.contains(Nat.One) // only ask the user if they picked Nat.One
      _     <- ask[String]("textfield-test", validation = Rule.maxLength(254)) // should render a normal textfield
      ta    <- ask[String]("text-area-test", validation = Rule.maxLength(255)) // should render a textarea on account of the length
      _     <- tell("check-your-answers-test", CYATestThing(ns, anInt.getOrElse(0), ta))
      _     <- end("an-end", Html("hiya!")) when ns.contains(Nat.Three)
    } yield ()

    val wm = interpret(simpleJourney)
    wm.run(id, config = JourneyConfig(leapAhead = true)) { date =>
      Future.successful(Ok(date.toString))
    }

  }

}
