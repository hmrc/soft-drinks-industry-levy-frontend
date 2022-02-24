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

import ltbs.uniform._
import common.web._
import interpreters.playframework._
import play.api.i18n.MessagesApi
import play.api.mvc.{AnyContent, Request}
import play.twirl.api.{Html, HtmlFormat}
import views.html.uniform
import sdil.config.AppConfig
import cats.implicits._
import sdil.models.{Address, ContactDetails, SmallProducer, Warehouse}
import sdil.uniform.SdilComponents
import uk.gov.hmrc.domain.Modulus23Check

import scala.concurrent.duration._
import scala.language.{higherKinds, postfixOps}

trait HmrcPlayInterpreter extends PlayInterpreter[Html] with SdilComponents with InferWebAsk[Html] with Modulus23Check {

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
  // DO NOT REMOVE IT
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

  // Address validation logic
  val addressRegex = """^[A-Za-z0-9 \-,.&'\/]*$"""
  val postcodeRegex = "^[A-Z]{1,2}[0-9][0-9A-Z]?\\s?[0-9][A-Z]{2}$|BFPO\\s?[0-9]{1,5}$"
  implicit val askAddress: WebAsk[Html, Address] = gen[Address].simap {
    case Address(line1, line2, line3, line4, postcode)
        if (!line1.matches(addressRegex)
          && !line2.matches(addressRegex)
          && !line3.matches(addressRegex)
          && !line4.matches(addressRegex)
          && !postcode.matches(postcodeRegex)) =>
      Left(
        ErrorMsg("invalid").toTree.prefixWith("line1") ++
          ErrorMsg("invalid").toTree.prefixWith("line2") ++
          ErrorMsg("invalid").toTree.prefixWith("line3") ++
          ErrorMsg("invalid").toTree.prefixWith("line4") ++
          ErrorMsg("invalid").toTree.prefixWith("postcode")
      )

    case Address(line1, line2, line3, _, postcode)
        if !line1.matches("""^[A-Za-z0-9 \-,.&'\/]*$""") &&
          !line2.matches("""^[A-Za-z0-9 \-,.&'\/]*$""") &&
          !line3.matches("""^[A-Za-z0-9 \-,.&'\/]*$""") &&
          !postcode.matches(postcodeRegex) =>
      Left(
        ErrorMsg("invalid").toTree.prefixWith("line1") ++
          ErrorMsg("invalid").toTree.prefixWith("line2") ++
          ErrorMsg("invalid").toTree.prefixWith("line3") ++
          ErrorMsg("invalid").toTree.prefixWith("postcode")
      )

    case Address(line1, line2, _, _, postcode)
        if !line1.matches("""^[A-Za-z0-9 \-,.&'\/]*$""") &&
          !line2.matches("""^[A-Za-z0-9 \-,.&'\/]*$""") &&
          !postcode.matches(postcodeRegex) =>
      Left(
        ErrorMsg("invalid").toTree.prefixWith("line1") ++
          ErrorMsg("invalid").toTree.prefixWith("line2") ++
          ErrorMsg("invalid").toTree.prefixWith("postcode")
      )

    case Address(line1, _, _, _, _) if !line1.matches("""^[A-Za-z0-9 \-,.&'\/]*$""") =>
      Left(
        ErrorMsg("invalid").toTree.prefixWith("line1")
      )

    case Address(line1, line2, _, _, postcode) if line1.isEmpty && line2.isEmpty && postcode.isEmpty =>
      Left(
        ErrorMsg("required").toTree.prefixWith("line1") ++
          ErrorMsg("required").toTree.prefixWith("line2") ++
          ErrorMsg("required").toTree.prefixWith("postcode")
      )
    case Address(line1, line2, _, _, _) if line1.isEmpty && line2.isEmpty =>
      Left(ErrorMsg("required").toTree.prefixWith("line1") ++ ErrorMsg("required").toTree.prefixWith("line2"))
    case Address(line1, _, _, _, _) if line1.isEmpty     => Left(ErrorMsg("required").toTree.prefixWith("line1"))
    case Address(line1, _, _, _, _) if line1.length > 35 => Left(ErrorMsg("max").toTree.prefixWith("line1"))
    case Address(line1, _, _, _, _) if !line1.matches("""^[A-Za-z0-9 \-,.&'\/]*$""") =>
      Left(ErrorMsg("invalid").toTree.prefixWith("line1") ++ ErrorMsg("invalid").toTree.prefixWith("line2"))
    case Address(_, line2, _, _, _) if line2.isEmpty     => Left(ErrorMsg("required").toTree.prefixWith("line2"))
    case Address(_, line2, _, _, _) if line2.length > 35 => Left(ErrorMsg("max").toTree.prefixWith("line2"))
    case Address(_, line2, _, _, _) if !line2.matches("""^[A-Za-z0-9 \-,.&'\/]*$""") =>
      Left(ErrorMsg("invalid").toTree.prefixWith("line2") ++ ErrorMsg("invalid").toTree.prefixWith("line3"))
    case Address(_, _, line3, _, _) if line3.length > 35 => Left(ErrorMsg("max").toTree.prefixWith("line3"))
    case Address(_, _, line3, _, _) if !line3.matches("""^[A-Za-z0-9 \-,.&'\/]*$""") =>
      Left(ErrorMsg("invalid").toTree.prefixWith("line3") ++ ErrorMsg("invalid").toTree.prefixWith("line4"))
    case Address(_, _, _, line4, _) if line4.length > 35 => Left(ErrorMsg("max").toTree.prefixWith("line4"))
    case Address(_, _, _, line4, _) if !line4.matches("""^[A-Za-z0-9 \-,.&'\/]*$""") =>
      Left(ErrorMsg("invalid").toTree.prefixWith("line2") ++ ErrorMsg("invalid").toTree.prefixWith("line3"))
    case Address(_, _, _, _, postcode) if postcode.isEmpty => Left(ErrorMsg("required").toTree.prefixWith("postcode"))
    case Address(_, _, _, _, postcode)
        if !postcode.matches("^[A-Z]{1,2}[0-9][0-9A-Z]?\\s?[0-9][A-Z]{2}$|BFPO\\s?[0-9]{1,5}$") =>
      Left(ErrorMsg("invalid").toTree.prefixWith("postcode") ++ ErrorMsg("invalid").toTree.prefixWith("postcode"))
    case Address(_, _, _, _, postcode) if !postcode.matches("""^[A-Za-z0-9 _]*[A-Za-z0-9][A-Za-z0-9 _]*$""") =>
      Left(ErrorMsg("special").toTree.prefixWith("postcode") ++ ErrorMsg("invalid").toTree.prefixWith("postcode"))
    case other => other.asRight
  }(identity)

  // Warehouse validation logic
  implicit val askWarehouse: WebAsk[Html, Warehouse] = gen[Warehouse].simap {
    case Warehouse(tradingName, _) if tradingName.length > 160 => Left(ErrorMsg("max").toTree.prefixWith("tradingName"))
    case Warehouse(tradingName, _) if !tradingName.matches("""^[a-zA-Z0-9 '.&\\/]*$""") =>
      Left(ErrorMsg("invalid").toTree.prefixWith("tradingName"))
    case other => other.asRight
  }(identity)

  private val emailRegex =
    """^[a-zA-Z0-9\.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$"""

  // ContactDetails validation logic
  implicit val askContactDetails: WebAsk[Html, ContactDetails] = gen[ContactDetails].simap {
    case ContactDetails(fullName, position, phoneNumber, email)
        if fullName.isEmpty && position.isEmpty && phoneNumber.isEmpty && email.isEmpty =>
      Left(
        ErrorMsg("required").toTree.prefixWith("fullName") ++
          ErrorMsg("required").toTree.prefixWith("position") ++
          ErrorMsg("required").toTree.prefixWith("phoneNumber") ++
          ErrorMsg("required").toTree.prefixWith("email")
      )
    case ContactDetails(fullName, position, phoneNumber, _)
        if fullName.isEmpty && position.isEmpty && phoneNumber.isEmpty =>
      Left(
        ErrorMsg("required").toTree.prefixWith("fullName") ++
          ErrorMsg("required").toTree.prefixWith("position") ++
          ErrorMsg("required").toTree.prefixWith("phoneNumber")
      )
    case ContactDetails(fullName, position, _, email) if fullName.isEmpty && position.isEmpty && email.isEmpty =>
      Left(
        ErrorMsg("required").toTree.prefixWith("fullName") ++
          ErrorMsg("required").toTree.prefixWith("position") ++
          ErrorMsg("required").toTree.prefixWith("email")
      )
    case ContactDetails(fullName, _, phoneNumber, email) if fullName.isEmpty && phoneNumber.isEmpty && email.isEmpty =>
      Left(
        ErrorMsg("required").toTree.prefixWith("fullName") ++
          ErrorMsg("required").toTree.prefixWith("phoneNumber") ++
          ErrorMsg("required").toTree.prefixWith("email")
      )
    case ContactDetails(_, position, phoneNumber, email) if position.isEmpty && phoneNumber.isEmpty && email.isEmpty =>
      Left(
        ErrorMsg("required").toTree.prefixWith("position") ++
          ErrorMsg("required").toTree.prefixWith("phoneNumber") ++
          ErrorMsg("required").toTree.prefixWith("email")
      )
    case ContactDetails(fullName, position, _, _) if fullName.isEmpty && position.isEmpty =>
      Left(
        ErrorMsg("required").toTree.prefixWith("fullName") ++
          ErrorMsg("required").toTree.prefixWith("position")
      )
    case ContactDetails(fullName, _, phoneNumber, _) if fullName.isEmpty && phoneNumber.isEmpty =>
      Left(
        ErrorMsg("required").toTree.prefixWith("fullName") ++
          ErrorMsg("required").toTree.prefixWith("phoneNumber")
      )
    case ContactDetails(fullName, _, _, email) if fullName.isEmpty && email.isEmpty =>
      Left(
        ErrorMsg("required").toTree.prefixWith("fullName") ++
          ErrorMsg("required").toTree.prefixWith("email")
      )
    case ContactDetails(_, position, phoneNumber, _) if position.isEmpty && phoneNumber.isEmpty =>
      Left(
        ErrorMsg("required").toTree.prefixWith("position") ++
          ErrorMsg("required").toTree.prefixWith("phoneNumber")
      )
    case ContactDetails(_, position, _, email) if position.isEmpty && email.isEmpty =>
      Left(
        ErrorMsg("required").toTree.prefixWith("position") ++
          ErrorMsg("required").toTree.prefixWith("email")
      )
    case ContactDetails(_, _, phoneNumber, email) if phoneNumber.isEmpty && email.isEmpty =>
      Left(
        ErrorMsg("required").toTree.prefixWith("phoneNumber") ++
          ErrorMsg("required").toTree.prefixWith("email")
      )

    case ContactDetails(fullName, _, _, _) if fullName.isEmpty =>
      Left(ErrorMsg("required").toTree.prefixWith("fullName"))
    case ContactDetails(fullName, _, _, _) if fullName.length > 40 =>
      Left(ErrorMsg("max").toTree.prefixWith("fullName"))
    case ContactDetails(fullName, _, _, _) if !fullName.matches("^[a-zA-Z &`\\-\\'\\.^]{1,40}$") =>
      Left(ErrorMsg("invalid").toTree.prefixWith("fullName"))
    case ContactDetails(_, position, _, _) if position.isEmpty =>
      Left(ErrorMsg("required").toTree.prefixWith("position"))
    case ContactDetails(_, position, _, _) if position.length > 155 =>
      Left(ErrorMsg("max").toTree.prefixWith("position"))
    case ContactDetails(_, position, _, _) if !position.matches("^[a-zA-Z &`\\-\\'\\.^]{1,155}$") =>
      Left(ErrorMsg("invalid").toTree.prefixWith("position"))
    case ContactDetails(_, _, phoneNumber, _) if phoneNumber.isEmpty =>
      Left(ErrorMsg("required").toTree.prefixWith("phoneNumber"))
    case ContactDetails(_, _, phoneNumber, _) if phoneNumber.length > 24 =>
      Left(ErrorMsg("max").toTree.prefixWith("phoneNumber"))
    case ContactDetails(_, _, phoneNumber, _) if !phoneNumber.matches("^[A-Z0-9 )/(\\-*#+]{1,24}$") =>
      Left(ErrorMsg("invalid").toTree.prefixWith("phoneNumber"))
    case ContactDetails(_, _, _, email) if email.isEmpty      => Left(ErrorMsg("required").toTree.prefixWith("email"))
    case ContactDetails(_, _, _, email) if email.length > 132 => Left(ErrorMsg("max").toTree.prefixWith("email"))
    case ContactDetails(_, _, _, email) if !email.matches(emailRegex) =>
      Left(ErrorMsg("invalid").toTree.prefixWith("email"))
    case other => other.asRight
  }(identity)

  implicit val askSmallProducer: WebAsk[Html, SmallProducer] = gen[SmallProducer].simap {
    case SmallProducer(_, sdilRef, litreage) if sdilRef.isEmpty && litreage._1.isEmpty && litreage._2.isEmpty =>
      Left(
        ErrorMsg("required").toTree.prefixWith("sdilRef") ++
          ErrorMsg("required").toTree.prefixWith("litreage._1") ++
          ErrorMsg("required").toTree.prefixWith("litreage._2")
      )
    case SmallProducer(_, sdilRef, litreage) if sdilRef.isEmpty && litreage._1.isEmpty =>
      Left(
        ErrorMsg("required").toTree.prefixWith("sdilRef") ++
          ErrorMsg("required").toTree.prefixWith("litreage._1")
      )
    case SmallProducer(_, sdilRef, litreage) if sdilRef.isEmpty && litreage._2.isEmpty =>
      Left(
        ErrorMsg("required").toTree.prefixWith("sdilRef") ++
          ErrorMsg("required").toTree.prefixWith("litreage._2")
      )
    case SmallProducer(_, sdilRef, _) if !sdilRef.matches("^X[A-Z]SDIL000[0-9]{6}$") =>
      Left(ErrorMsg("invalid").toTree.prefixWith("sdilRef"))
    case SmallProducer(_, sdilRef, _) if !isCheckCorrect(sdilRef, 1) =>
      Left(ErrorMsg("invalid").toTree.prefixWith("sdilRef"))

    case SmallProducer(alias, _, _) if alias.length > 160 => Left(ErrorMsg("max").toTree.prefixWith("alias"))
    case SmallProducer(_, sdilRef, _) if sdilRef.isEmpty  => Left(ErrorMsg("required").toTree.prefixWith("sdilRef"))
    case other                                            => other.asRight
  }(identity)
}
