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

package sdil.uniform

import cats.syntax.all._
import cats.data.Validated
import ltbs.uniform._
import validation._
import ltbs.uniform.common.web._
import play.api.data._
import play.api.i18n.Messages
import play.twirl.api.Html
import sdil.models._
import sdil.models.backend.Site
import views.html.uniform
import sdil.uniform.AdaptMessages.ufMessagesToPlayMessages
import views.html.uniform.fragments.date_new
import enumeratum._
import java.time.LocalDate
import sdil.controllers.Subset

trait SdilComponentsNew {

  def ufViews: views.uniform.Uniform

  type FormHtml[A] = WebAsk[Html, A]

  implicit val booleanForm: WebAsk[Html, Boolean] = new WebAsk[Html, Boolean] {
    def decode(out: Input): Either[ErrorTree, Boolean] =
      out
        .toField[Boolean](
          x =>
            Rule.nonEmpty[String].apply(x) andThen (y =>
              Validated
                .catchOnly[IllegalArgumentException](y.toBoolean)
                .leftMap(_ => ErrorMsg("invalid").toTree)))
        .toEither

    def encode(in: Boolean): Input = Input.one(List(in.toString))

    def render(
      pageIn: PageIn[Html],
      stepDetails: StepDetails[Html, Boolean]
    ): Option[Html] = Some(
      views.html.softdrinksindustrylevy.helpers.radios(
        stepDetails.stepKey,
        stepDetails.fieldKey,
        stepDetails.tell,
        radioOptions = true.toString :: false.toString :: Nil,
        existing = decode(stepDetails.data).toOption.map(_.toString),
        stepDetails.errors,
        pageIn.messages
      )
    )
  }

  implicit val stringField = new FormHtml[String] {

    def decode(out: Input): Either[ErrorTree, String] =
      out.toStringField().toEither

    def encode(in: String): Input = Input.one(List(in))

    def render(
      pageIn: PageIn[Html],
      stepDetails: StepDetails[Html, String]
    ): Option[Html] = Some {
      import stepDetails._
      val value = decode(data).toOption.getOrElse("")

      val max = validation.subRules
        .collect {
          case Rule.MaxLength(h, _)     => Some(h)
          case Rule.LengthBetween(_, h) => Some(h)
        }
        .foldLeft(none[Int]) {
          case (None, x)          => x
          case (Some(a), Some(b)) => Some(Math.min(a, b))
          case _                  => None
        }

      val control = max match {
        case Some(x) if x >= 255 =>
          views.html.softdrinksindustrylevy.helpers.textArea_new(fieldKey, value, errors)(pageIn.messages)
        case _ =>
          views.html.softdrinksindustrylevy.helpers.inputText_new(fieldKey, value, errors)(pageIn.messages)
      }

      views.html.softdrinksindustrylevy.helpers.surround(stepKey, fieldKey, tell, errors, pageIn.messages)(control)

    }
  }

  implicit val intField: WebAsk[Html, Int] =
    stringField.simap(x => {
      Rule.nonEmpty[String].apply(x) andThen
        Transformation.catchOnly[NumberFormatException]("not-a-number")(_.toInt)
    }.toEither)(_.toString)

  implicit val longField: WebAsk[Html, Long] =
    stringField.simap(x => {
      Rule.nonEmpty[String].apply(x) andThen
        Transformation.catchOnly[NumberFormatException]("not-a-number")(_.toLong)
    }.toEither)(_.toString)

  implicit val twirlDateField: WebAsk[Html, LocalDate] =
    new WebAsk[Html, LocalDate] {

      def decode(out: Input): Either[ErrorTree, LocalDate] = {

        def intAtKey(key: String): Validated[Map[String, List[String]], Int] =
          Validated
            .fromOption(
              out.valueAt(key).flatMap { _.find(_.trim.nonEmpty) },
              Map("empty" -> List(key))
            )
            .andThen(
              x =>
                Validated
                  .catchOnly[NumberFormatException](x.toInt)
                  .leftMap(_ => Map("nan" -> List(key))))
            .andThen(
              x =>
                Validated.cond(
                  (key, x) match {
                    case ("day", n)   => n > 0 && n <= 31
                    case ("month", n) => n > 0 && n <= 12
                    case ("year", n)  => n.toString.length == 4
                    case _            => false
                  },
                  x,
                  Map("invalid" -> List(key))
              ))

        (
          intAtKey("year"),
          intAtKey("month"),
          intAtKey("day")
        ).tupled match {
          case Validated.Valid((y, m, d)) =>
            Either
              .catchOnly[java.time.DateTimeException] {
                LocalDate.of(y, m, d)
              }
              .leftMap(_ => ErrorTree.oneErr(ErrorMsg("not-a-date")))
          case Validated.Invalid(errors) =>
            (errors.get("empty"), errors.get("nan"), errors.get("invalid")) match {
              case (Some(empty), _, _)   => Left(ErrorMsg(empty.reverse.mkString("-and-") + ".empty").toTree)
              case (_, Some(nan), _)     => Left(ErrorMsg(nan.reverse.mkString("-and-") + ".nan").toTree)
              case (_, _, Some(invalid)) => Left(ErrorMsg(invalid.reverse.mkString("-and-") + ".invalid").toTree)
              case _ =>
                Left(ErrorTree.oneErr(ErrorMsg("not-a-date")))
            }
        }
      }

      def encode(in: LocalDate): Input =
        Map(
          List("year")  -> in.getYear(),
          List("month") -> in.getMonthValue(),
          List("day")   -> in.getDayOfMonth()
        ).mapValues(_.toString.pure[List])

      def render(
        pageIn: PageIn[Html],
        stepDetails: StepDetails[Html, LocalDate]
      ): Option[Html] = Some {
        date_new(stepDetails.stepKey, stepDetails.data, stepDetails.errors, pageIn.messages)
      }
    }

  implicit def askUkAddress(implicit underlying: FormHtml[Address]): FormHtml[backend.UkAddress] =
    underlying.simap(backend.UkAddress.fromAddress(_).asRight)(Address.fromUkAddress)

  implicit def askSetEnum[E <: EnumEntry](implicit enum: Enum[E]): FormHtml[Set[E]] = new FormHtml[Set[E]] {

    def decode(out: Input): Either[ErrorTree, Set[E]] = {
      val strings = out.valueAtRoot.getOrElse(Nil)
      Right(strings.flatMap(enum.withNameOption).toSet)
    }

    def encode(in: Set[E]): Input =
      Map(List.empty -> in.map(_.entryName).toList)

    def render(
      pageIn: PageIn[Html],
      stepDetails: StepDetails[Html, Set[E]]
    ): Option[Html] = Some {
      // work out what the available options are - if a subset rule is
      // defined only show the options they're allowed to select
      // otherwise show the full enum.values
      val options = stepDetails.validation.subRules
        .collectFirst {
          case Subset(opts) => opts.toList
        }
        .getOrElse(enum.values)

      val existing = decode(stepDetails.data).toOption.getOrElse(Set.empty)
      views.html.softdrinksindustrylevy.helpers.checkboxes(
        stepDetails.stepKey,
        stepDetails.fieldKey,
        stepDetails.tell,
        options.map(e => e.entryName -> existing.contains(e)),
        stepDetails.errors,
        pageIn.messages
      )
    }

  }

  implicit val tellListSite = new WebTell[Html, WebAskList.ListingTable[Site]] {
    def render(
      in: WebAskList.ListingTable[Site],
      key: List[String],
      pageIn: PageIn[Html]
    ): Option[Html] = Some(
      views.html.softdrinksindustrylevy.helpers.listing_table(
        key.last,
        in.value.map { x =>
          Html(x.toString)
        },
        pageIn.messages
      )
    )
  }

  implicit val tellListSmallProducer = new WebTell[Html, WebAskList.ListingTable[SmallProducer]] {
    def render(
      in: WebAskList.ListingTable[SmallProducer],
      key: List[String],
      pageIn: PageIn[Html]
    ): Option[Html] = Some(
      views.html.softdrinksindustrylevy.helpers.listing_table(
        key.last,
        in.value.map { x =>
          Html(x.toString)
        },
        pageIn.messages
      )
    )
  }

  implicit val tellOldTemplates = new WebTell[Html, Messages => Html] {
    def render(
      in: Messages => Html,
      key: List[String],
      pageIn: PageIn[Html]
    ): Option[Html] = Some(
      in(AdaptMessages.ufMessagesToPlayMessages(pageIn.messages))
    )
  }

}

sealed trait OrganisationType extends EnumEntry
object OrganisationType extends Enum[OrganisationType] {
  val values = findValues
  case object limitedCompany extends OrganisationType
  case object limitedLiabilityPartnership extends OrganisationType
  case object partnership extends OrganisationType
  case object soleTrader extends OrganisationType
  case object unincorporatedBody extends OrganisationType
}

sealed trait OrganisationTypeSoleless extends EnumEntry
object OrganisationTypeSoleless extends Enum[OrganisationTypeSoleless] {
  val values = findValues
  case object limitedCompany extends OrganisationTypeSoleless
  case object limitedLiabilityPartnership extends OrganisationTypeSoleless
  case object partnership extends OrganisationTypeSoleless
  case object unincorporatedBody extends OrganisationTypeSoleless
}

sealed trait ProducerType extends EnumEntry
object ProducerType extends Enum[ProducerType] {
  val values = findValues
  case object Large extends ProducerType
  case object Small extends ProducerType
  case object Not extends ProducerType
}
