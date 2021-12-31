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

import ltbs.uniform._
import common.web._
import interpreters.playframework._
import play.api.i18n.MessagesApi
import play.api.mvc.{AnyContent, Request}
import play.twirl.api.{Html, HtmlFormat}
import views.html.uniform
import sdil.config.AppConfig
import cats.implicits._
import sdil.models.{Address, Warehouse}
import sdil.uniform.SdilComponentsNew

import scala.concurrent.duration._
import scala.language.{higherKinds, postfixOps}

trait HmrcPlayInterpreter extends PlayInterpreter[Html] with SdilComponentsNew with InferWebAsk[Html] {

  val config: AppConfig
  def messagesApi: MessagesApi
  def ufViews: views.uniform.Uniform
  def defaultBackLink: String

  lazy private val futureAdapter = FutureAdapter.rerunOnStateChange[Html](15.minutes)
  implicit protected def futAdapt[A: Codec] = futureAdapter.apply[A]

  implicit def messages(
    implicit request: Request[AnyContent]
  ): UniformMessages[Html] = {
    messagesApi.preferred(request).convertMessages() |+|
      UniformMessages.bestGuess
  }.map { Html(_) }
//    UniformMessages.attentionSeeker.map(HtmlFormat.escape)
  def pageChrome(
    key: List[String],
    errors: ErrorTree,
    html: Option[Html],
    breadcrumbs: List[String],
    request: Request[AnyContent],
    messages: UniformMessages[Html]
  ): Html = {
    import play.filters.csrf._
    import play.filters.csrf.CSRF.Token
    val Token(_, csrf: String) = CSRF.getToken(request).get
    ufViews.base(key, errors, html, breadcrumbs, csrf, defaultBackLink)(messages, request, config)
  }

  def renderAnd[T](
    pageIn: PageIn[Html],
    stepDetails: StepDetails[Html, T],
    members: Seq[(String, Html)]
  ): Html = members.toList match {
    case (_, sole) :: Nil => sole
    case many =>
      views.html.softdrinksindustrylevy.helpers.surround(
        stepDetails.stepKey,
        stepDetails.fieldKey,
        stepDetails.tell,
        stepDetails.errors,
        pageIn.messages
      )(many.map(_._2): _*)
  }

  /** allows using progressive reveal for options, eithers or other sealed trait hierarchies
    *  */
  def renderOr[T](
    pageIn: PageIn[Html],
    stepDetails: StepDetails[Html, T],
    alternatives: Seq[(String, Option[Html])],
    selected: Option[String]
  ): Html = {

    // in some cases we might want options to appear in a sequence other than their natural order,
    // alternatively we could implement an implicit `WebAsk[Html, Option[A]]` (for example)
    // but this reduces the amount of logic needed
    val reordered = alternatives.map(_._1).toList match {
      case "None" :: "Some" :: Nil => alternatives.reverse
      case _                       => alternatives
    }

    val condContent = views.html.softdrinksindustrylevy.helpers
      .conditional_content(stepDetails.fieldKey, reordered.map(_._1), selected, reordered.collect {
        case (k, Some(v)) => (k, v)
      }.toMap)

    views.html.softdrinksindustrylevy.helpers.radios(
      stepDetails.stepKey,
      stepDetails.fieldKey,
      stepDetails.tell,
      reordered.map(_._1),
      selected,
      stepDetails.errors,
      pageIn.messages,
      reordered.collect { case (k, Some(v)) => (k, v) }.toMap,
      condContent.some
    )
  }

  implicit override def unitField = new WebAsk[Html, Unit] {
    def decode(out: Input): Either[ltbs.uniform.ErrorTree, Unit] = Right(())
    def encode(in: Unit): Input = Input.empty
    def render(
      pageIn: PageIn[Html],
      stepDetails: StepDetails[Html, Unit]
    ): Option[Html] = Some {
      views.html.softdrinksindustrylevy.helpers
        .surround(stepDetails.stepKey, stepDetails.fieldKey, stepDetails.tell, stepDetails.errors, pageIn.messages)(
          Html(s"""<input type="hidden" name="${stepDetails.fieldKey.mkString(".")}" value="()" />"""))
    }
  }

  implicit override def nothingField = new WebAsk[Html, Nothing] {
    def decode(out: Input): Either[ltbs.uniform.ErrorTree, Nothing] =
      Left(ErrorMsg("tried to decode to nothing").toTree)

    def encode(in: Nothing): Input =
      sys.error("encoding nothing is not possible!")

    def render(
      pageIn: PageIn[Html],
      stepDetails: StepDetails[Html, Nothing]
    ): Option[Html] = stepDetails.tell
//      Some(
//        views.html.softdrinksindustrylevy.helpers
//          .end_surround(
//            stepDetails.stepKey,
//            stepDetails.fieldKey.head,
//            stepDetails.tell,
//            stepDetails.errors,
//            pageIn.messages)()
//      )
  }
// Validations starts here
  //TODO implicits are needed for Address, Warehouse, ContactDetails, SmallProducer
  //Copy the validation from app.sdil.uniform.SdilComponents

  implicit val askAddress: WebAsk[Html, Address] = gen[Address].simap {
    case Address(line1, _, _, _, _) if line1.isEmpty     => Left(ErrorTree.oneErr(ErrorMsg("line1.required")))
    case Address(line1, _, _, _, _) if line1.length > 30 => Left(ErrorTree.oneErr(ErrorMsg("line1.max")))
    case Address(line1, _, _, _, _) if !line1.matches("""^[A-Za-z0-9 \-,.&'\/]*$""") =>
      Left(ErrorTree.oneErr(ErrorMsg("invalid")))
    case other => other.asRight
  }(identity)

  implicit val askWarehouse: WebAsk[Html, Warehouse] = gen[Warehouse].simap {
    case Warehouse(tradingName, _) if tradingName.isEmpty => Left(ErrorTree.oneErr(ErrorMsg("tradingName.required")))
    case other                                            => other.asRight
  }(identity)

}
