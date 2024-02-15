/*
 * Copyright 2024 HM Revenue & Customs
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

import cats.data.Validated
import cats.syntax.all._
import enumeratum._
import ltbs.uniform._
import ltbs.uniform.common.web._
import ltbs.uniform.validation.{Rule, _}
import play.api.i18n.Messages
import play.twirl.api.Html
import sdil.controllers.Subset
import sdil.journeys.VariationsJourney.Change
import sdil.models._
import sdil.models.backend.{Site, UkAddress}
import sdil.models.variations.{Convert, RegistrationVariationData}
import views.html.uniform
import views.html.uniform.fragments.date_new
import play.api.libs.json._
import play.api.libs.functional.syntax.{unlift, _}
import sdil.controllers.VerifyController.{combine, required}

import java.time.LocalDate
import scala.language.postfixOps

trait SdilComponents {

  def ufViews: views.uniform.Uniform

  def journeyConfig: JourneyConfig = JourneyConfig(askFirstListItem = true, leapAhead = true)

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
      stepDetails: StepDetails[Html, String],
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
          views.html.softdrinksindustrylevy.helpers.textArea_new(fieldKey, value, errors)
        case _ =>
          views.html.softdrinksindustrylevy.helpers.inputText_new(fieldKey, value, errors)(pageIn.messages)
      }

      views.html.softdrinksindustrylevy.helpers
        .surround(stepKey, fieldKey, tell, errors, pageIn.messages)(control)
    }
  }

  implicit val intField: WebAsk[Html, Int] =
    stringField.simap(x => {
      Rule.nonEmpty[String].apply(x) andThen
        Transformation.catchOnly[NumberFormatException]("not-a-number")(_.toInt)
    }.toEither)(_.toString)

  implicit val bdField: WebAsk[Html, BigDecimal] =
    stringField.simap(x => {
      Rule.nonEmpty[String].apply(x) andThen
        Transformation.catchOnly[NumberFormatException]("not-a-number")(x => BigDecimal(x))
    }.toEither)(_.toString)

  implicit val longField: WebAsk[Html, Long] =
    stringField.simap(x => {
      Rule.nonEmpty[String].apply(x) andThen
        Rule.cond(_.length <= 15, "max") andThen
        Transformation.catchOnly[NumberFormatException]("not-a-number")(_.replace(",", "").toLong)
    }.toEither)(_.toString)

  implicit val returnPeriodAsk = new WebAsk[Html, ReturnPeriod] {
    override def render(pageIn: PageIn[Html], stepDetails: StepDetails[Html, ReturnPeriod]): Option[Html] = {
      val ret = stepDetails.validation.subRules.collectFirst {
        case Rule.In(allowed, _) =>
          views.html.softdrinksindustrylevy.helpers
            .radios(
              stepDetails.stepKey,
              stepDetails.fieldKey,
              stepDetails.tell,
              radioOptions = allowed.map(in => in.year + "." + in.quarter),
              existing = decode(stepDetails.data).toOption.map(_.toString),
              stepDetails.errors,
              pageIn.messages
            )
      }
      if (ret.isEmpty) {
        throw new NotImplementedError("No adjustable returns found")
      } else {
        ret
      }
    }

    override def encode(in: ReturnPeriod): Input = Input.one(List(in.year + "." + in.quarter))

    override def decode(out: Input): Either[ErrorTree, ReturnPeriod] =
      out
        .toStringField()
        .toEither
        .flatMap(
          x =>
            Either
              .catchOnly[NumberFormatException](x.split("\\.").toList.map(_.toInt))
              .leftMap(_ => ErrorMsg("required").toTree)
        )
        .flatMap {
          case year :: quarter :: Nil => Right(ReturnPeriod(year, quarter))
          case _                      => Left(ErrorMsg(s"returnPeriod cannot be converted to Int").toTree)
        }
  }

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
        ).mapValues(_.toString.pure[List]).toMap

      def render(
        pageIn: PageIn[Html],
        stepDetails: StepDetails[Html, LocalDate]
      ): Option[Html] = Some {
        val control = date_new(stepDetails.stepKey, stepDetails.data, stepDetails.errors, pageIn.messages)
        views.html.softdrinksindustrylevy.helpers
          .surround(
            stepDetails.stepKey,
            stepDetails.fieldKey,
            stepDetails.tell,
            stepDetails.errors,
            pageIn.messages
          )(control)
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

  implicit val tellCYAVariations = new WebTell[Html, CYA[Change]] {

    def closedWarehouseSites(variation: RegistrationVariationData): List[Site] = {
      val old = variation.original.warehouseSites
        .filter { x =>
          x.closureDate.fold(true) { _.isAfter(LocalDate.now) }
        }
        .map(x => x.address)

      val newList = variation.updatedWarehouseSites
        .filter { x =>
          x.closureDate.fold(true) { _.isAfter(LocalDate.now) }
        }
        .map(x => x.address)

      val diff = old diff newList

      variation.original.warehouseSites.filter { site =>
        diff.exists { address =>
          compareAddress(site.address, address)
        }
      }
    }.toList

    def closedPackagingSites(variation: RegistrationVariationData): List[Site] = {
      val old = variation.original.productionSites
        .filter { x =>
          x.closureDate.fold(true) { _.isAfter(LocalDate.now) }
        }
        .map(x => x.address)

      val newList = variation.updatedProductionSites
        .filter { x =>
          x.closureDate.fold(true) { _.isAfter(LocalDate.now) }
        }
        .map(x => x.address)

      val diff = old diff newList

      variation.original.productionSites.filter { site =>
        diff.exists { address =>
          compareAddress(site.address, address)
        }
      }
    }

    def newPackagingSites(variation: RegistrationVariationData): List[Site] = {
      val old = variation.original.productionSites.map(x => x.address)
      val updated = variation.updatedProductionSites.map(x => x.address)
      val newAddress = updated diff old

      variation.updatedProductionSites.filter { site =>
        newAddress.exists { address =>
          compareAddress(site.address, address)
        }
      }
    }.toList

    def newWarehouseSites(variation: RegistrationVariationData): List[Site] = {
      val oldAddress = variation.original.warehouseSites.map(x => x.address)
      val updatedAddress = variation.updatedWarehouseSites.map(x => x.address) //This contains all the packaging sites new and old
      val newAddress = updatedAddress diff oldAddress

      variation.updatedWarehouseSites.filter { site =>
        newAddress.exists { address =>
          compareAddress(site.address, address)
        }
      }
    }.toList

    def compareAddress(a1: UkAddress, a2: UkAddress): Boolean =
      a1.postCode.equals(a2.postCode) && a1.lines.equals(a2.lines)

    override def render(
      in: CYA[Change],
      key: List[String],
      pageIn: PageIn[Html]
    ): Option[Html] = {
      implicit val msg = pageIn.messages
      in.value match {
        case Change.RegChange(data) =>
          uniform.fragments
            .variationsCYA(
              data,
              newPackagingSites(data),
              closedPackagingSites(data),
              newWarehouseSites(data),
              closedWarehouseSites(data),
              pageIn.breadcrumbs.map(_.mkString("/"))
            )
            .some
        case _ => ???
      }
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
        }
      )
    )
  }

  implicit val tellListWarehouse = new WebTell[Html, WebAskList.ListingTable[Warehouse]] {
    def render(
      in: WebAskList.ListingTable[Warehouse],
      key: List[String],
      pageIn: PageIn[Html]
    ): Option[Html] = Some(
      views.html.uniform.fragments.warehouse_table(
        key.last,
        in.value,
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
      views.html.uniform.fragments.small_producer_table(
        key.last,
        in.value,
        pageIn.messages
      )
    )
  }

  implicit val tellListAddress = new WebTell[Html, WebAskList.ListingTable[Address]] {
    def render(
      in: WebAskList.ListingTable[Address],
      key: List[String],
      pageIn: PageIn[Html]
    ): Option[Html] = Some(
      views.html.uniform.fragments.address_table(
        key.last,
        in.value,
        pageIn.messages
      )
    )
  }

  implicit val tellAddress = new WebTell[Html, Address] {
    def render(
      in: Address,
      key: List[String],
      pageIn: PageIn[Html]
    ): Option[Html] = Some(
      Html(s"<p tabindex='-1' class='lede'>Registered address: ${in.nonEmptyLines.mkString(", ")}")
    )
  }

  implicit val tellWarehouse = new WebTell[Html, Seq[String]] {
    def render(
      in: Seq[String],
      key: List[String],
      pageIn: PageIn[Html]
    ): Option[Html] = Some(
      Html(s"""
              |<p tabindex='-1' class='lede'>
              |${in.mkString(", ")}
              |</P>
           """.stripMargin)
    )
  }

  implicit val tellString = new WebTell[Html, String] {
    def render(
      in: String,
      key: List[String],
      pageIn: PageIn[Html]
    ): Option[Html] = Some(Html(s"<p tabindex='-1' class='lede'>$in</p>"))
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

object SdilComponents {
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

    case object XNot extends ProducerType
  }

  import play.api.data.validation.{Constraint, Invalid, Valid}
  import play.api.data.Forms._
  import play.api.data._
  lazy val addressMapping: Mapping[Address] = mapping(
    "line1"    -> mandatoryAddressLine("line1"),
    "line2"    -> mandatoryAddressLine("line2"),
    "line3"    -> optionalAddressLine("line3"),
    "line4"    -> optionalAddressLine("line4"),
    "postcode" -> postcode
  )(Address.apply)(Address.unapply)

  private def mandatoryAddressLine(key: String): Mapping[String] =
    text.transform[String](_.trim, s => s).verifying(combine(required(key), optionalAddressLineConstraint(key)))

  private def optionalAddressLine(key: String): Mapping[String] =
    text.transform[String](_.trim, s => s).verifying(optionalAddressLineConstraint(key))

  private def optionalAddressLineConstraint(key: String): Constraint[String] = Constraint {
    case a if !a.matches("""^[A-Za-z0-9 \-,.&'\/]*$""") => Invalid(s"error.$key.invalid")
    case b if b.length > 35                             => Invalid(s"error.$key.over")
    case _                                              => Valid
  }

  def postcode: Mapping[String] = {
    val postcodeRegex = "^[A-Z]{1,2}[0-9][0-9A-Z]?\\s?[0-9][A-Z]{2}$|BFPO\\s?[0-9]{1,5}$"
    val specialRegex = """^[A-Za-z0-9 _]*[A-Za-z0-9][A-Za-z0-9 _]*$"""

    text
      .transform[String](_.toUpperCase.trim, identity)
      .verifying(Constraint { x: String =>
        x match {
          case ""                               => Invalid("postcode.empty")
          case pc if !pc.matches(specialRegex)  => Invalid("postcode.special")
          case pc if !pc.matches(postcodeRegex) => Invalid("postcode.invalid")
          case _                                => Valid
        }
      })
  }

}
