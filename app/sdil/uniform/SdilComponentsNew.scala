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
import uk.gov.hmrc.uniform._
import views.html.uniform
import sdil.uniform.AdaptMessages.ufMessagesToPlayMessages
import views.html.uniform.fragments.date_new

import java.time.LocalDate

trait SdilComponentsNew /*extends FormHelpers*/ {

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
      pageKey: List[String],
      fieldKey: List[String],
      tell: Option[Html],
      breadcrumbs: Breadcrumbs,
      data: Input,
      errors: ErrorTree,
      messages: UniformMessages[Html]
    ): Option[Html] = Some(
      views.html.softdrinksindustrylevy.helpers.radios(
        fieldKey,
        tell,
        radioOptions = true.toString :: false.toString :: Nil,
        existing = decode(data).toOption.map(_.toString),
        errors,
        messages
      )
    )
  }

  implicit val stringField = new FormHtml[String] {

    def decode(out: Input): Either[ErrorTree, String] =
      out.toStringField().toEither

    def encode(in: String): Input = Input.one(List(in))

    def render(
      pageKey: List[String],
      fieldKey: List[String],
      tell: Option[Html],
      path: Breadcrumbs,
      data: Input,
      errors: ErrorTree,
      messages: UniformMessages[Html]
    ): Option[Html] = Some {
      val value = decode(data).toOption.getOrElse("")
      views.html.softdrinksindustrylevy.helpers.inputText_new(fieldKey, value, errors)(messages)
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
        pageKey: List[String],
        fieldKey: List[String],
        tell: Option[Html],
        path: Breadcrumbs,
        data: Input,
        errors: ErrorTree,
        messages: UniformMessages[Html]
      ): Option[Html] = Some {
        date_new(pageKey, data, errors, messages)
      }
    }

  // implicit val addressForm = new FormHtml[Address] {

  //   def decode(out: Input): Either[ErrorTree, Address] = ???
  //   def encode(in: Address): Input = ???

  //   def render(
  //     pageKey: List[String],
  //     fieldKey: List[String],
  //     tell: Option[Html],
  //     breadcrumbs: Breadcrumbs,
  //     data: Input,
  //     errors: ErrorTree,
  //     messages: UniformMessages[Html]
  //   ): Option[Html] = ???

  //   def asHtmlForm(key: String, form: Form[Address])(implicit messages: Messages): Html =
  //     uniform.fragments.address(key, form)
  // }
  /*
  implicit val smallProducerForm = new FormHtml[SmallProducer] {

    def decode(out: Input): Either[ErrorTree, SmallProducer] = ???
    def encode(in: SmallProducer): Input = ???

    def render(
      pageKey: List[String],
      fieldKey: List[String],
      tell: Option[Html],
      breadcrumbs: Breadcrumbs,
      data: Input,
      errors: ErrorTree,
      messages: UniformMessages[Html]
    ): Option[Html] = ???

    def asHtmlForm(key: String, form: Form[SmallProducer])(implicit messages: Messages): Html =
      uniform.fragments.smallProducer(key, form)
  }
   */
  /*
  implicit val litreageForm = new FormHtml[(Long, Long)] {

    def decode(out: Input): Either[ErrorTree, (Long, Long)] = ???
    def encode(in: (Long, Long)): Input = ???

    def render(
      pageKey: List[String],
      fieldKey: List[String],
      tell: Option[Html],
      breadcrumbs: Breadcrumbs,
      data: Input,
      errors: ErrorTree,
      messages: UniformMessages[Html]
    ): Option[Html] = ???

    def asHtmlForm(key: String, form: Form[(Long, Long)])(implicit messages: Messages): Html =
      uniform.fragments.litreage(key, form, approximate = true)
  }*/

  // implicit val siteForm = new FormHtml[Site] {

  //   def decode(out: Input): Either[ErrorTree, Site] = ???
  //   def encode(in: Site): Input = ???

  //   def render(
  //     pageKey: List[String],
  //     fieldKey: List[String],
  //     tell: Option[Html],
  //     breadcrumbs: Breadcrumbs,
  //     data: Input,
  //     errors: ErrorTree,
  //     messages: UniformMessages[Html]
  //   ): Option[Html] = ???

  //   def asHtmlForm(key: String, form: Form[Site])(implicit messages: Messages): Html =
  //     uniform.fragments.site(key, form)
  // }

//  implicit val siteProgressiveRevealHtml: HtmlShow[Site] = {
//
//    def lines(s: Site): String = {
//      val lines = s.tradingName.fold(s.address.lines.tail)(_ => s.address.lines)
//      lines.map { line =>
//        s"""<div class="address progressive-reveal">$line</div>"""
//      }
//    }.mkString
//
//    def visibleText(s: Site): String =
//      s.tradingName.fold(s.address.lines.head)(x => x)
//
//    HtmlShow.instance { site =>
//      Html(
//        s"""<details role="group">
//
//        <summary aria-controls="details-content-1" aria-expanded="false">
//          <span class="summary">${visibleText(site)}, ${site.address.postCode}</span>
//        </summary>""" +
//          lines(site) +
//          s"""<div class="address postal-code progressive-reveal">${site.address.postCode}</div>
//        </details>"""
//      )
//    }
//  }

  // implicit val contactDetailsForm = new FormHtml[ContactDetails] {

  //   def decode(out: Input): Either[ErrorTree, ContactDetails] = ???
  //   def encode(in: ContactDetails): Input = ???

  //   def render(
  //     pageKey: List[String],
  //     fieldKey: List[String],
  //     tell: Option[Html],
  //     breadcrumbs: Breadcrumbs,
  //     data: Input,
  //     errors: ErrorTree,
  //     messages: UniformMessages[Html]
  //   ): Option[Html] = ???

  //   def asHtmlForm(key: String, form: Form[ContactDetails])(implicit messages: Messages): Html =
  //     uniform.fragments.contactdetails(key, form)
  // }

  implicit def askUkAddress(implicit underlying: FormHtml[Address]): FormHtml[backend.UkAddress] =
    underlying.simap(backend.UkAddress.fromAddress(_).asRight)(Address.fromUkAddress)

//  implicit val addressHtml: HtmlShow[Address] =
//    HtmlShow.instance { address =>
//      val lines = address.nonEmptyLines.mkString("<br />")
//      Html(s"<div>$lines</div>")
//    }
//
//  implicit def showLitreage(implicit messages: Messages): HtmlShow[Litreage] = new HtmlShow[Litreage] {
//    def showHtml(l: Litreage): Html = l match {
//      case Litreage(lower, higher) =>
//        Html(
//          Messages("sdil.declaration.low-band") + f": $lower%,.0f" +
//            "<br>" +
//            Messages("sdil.declaration.high-band") + f": $higher%,.0f"
//        )
//    }
//  }

  implicit val tellListSite = new WebTell[Html, WebAskList.ListingTable[Site]] {
    def render(
      in: WebAskList.ListingTable[Site],
      key: String,
      messages: UniformMessages[Html]
    ): Option[Html] = Some(
      views.html.softdrinksindustrylevy.helpers.listing_table(
        key,
        in.value.map { x =>
          Html(x.toString)
        },
        messages
      )
    )
  }

  implicit val tellListSmallProducer = new WebTell[Html, WebAskList.ListingTable[SmallProducer]] {
    def render(
      in: WebAskList.ListingTable[SmallProducer],
      key: String,
      messages: UniformMessages[Html]
    ): Option[Html] = Some(
      views.html.softdrinksindustrylevy.helpers.listing_table(
        key,
        in.value.map { x =>
          Html(x.toString)
        },
        messages
      )
    )
  }

}
