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

import java.time.LocalDate

import cats.{Eq, Monoid}
import enumeratum._
import ltbs.play.scaffold.GdsComponents._
import ltbs.play.scaffold.SdilComponents.{packagingSiteForm, _}
import play.api.data.Forms._
import play.api.data.format.Formatter
import play.api.data.validation._
import play.api.data.{Form, Mapping, _}
import play.api.i18n.Messages
import play.api.libs.json._
import play.api.mvc.{AnyContent, Request, Result}
import play.twirl.api.Html
import sdil.config.AppConfig
import sdil.connectors.SoftDrinksIndustryLevyConnector
import sdil.models._
import sdil.models.backend.Site
import sdil.models.retrieved.RetrievedSubscription
import uk.gov.hmrc.domain.Modulus23Check
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import uk.gov.hmrc.uniform.HtmlShow.ops._
import uk.gov.hmrc.uniform._
import uk.gov.hmrc.uniform.playutil._
import uk.gov.hmrc.uniform.webmonad._
import views.html.uniform

import scala.concurrent._
import scala.concurrent.duration._
import cats.implicits._
import sdil.models.variations.ReturnVariationData

trait SdilWMController extends WebMonadController
    with FrontendController with Modulus23Check
{

  implicit def config: AppConfig

  val costLower = BigDecimal("0.18")
  val costHigher = BigDecimal("0.24")

  def returnAmount(sdilReturn: SdilReturn, isSmallProducer: Boolean): List[(String, (Long, Long), Int)] = {

    val ra = List(
      ("packaged-as-a-contract-packer", sdilReturn.packLarge, 1),
      ("exemptions-for-small-producers", sdilReturn.packSmall.map{_.litreage}.combineAll, 0),
      ("brought-into-uk", sdilReturn.importLarge, 1),
      ("brought-into-uk-from-small-producers", sdilReturn.importSmall, 0),
      ("claim-credits-for-exports", sdilReturn.export, -1),
      ("claim-credits-for-lost-damaged", sdilReturn.wastage, -1)
    )
    if(!isSmallProducer) // TODO - we need to find a way of ensuring this is correct for the period of the return and not just the latest state
      ("own-brands-packaged-at-own-sites", sdilReturn.ownBrand, 1) :: ra
    else
      ra
  }

  def calculateSubtotal(d: List[(String, (Long, Long), Int)]): BigDecimal = {
    d.map{case (_, (l,h), m) => costLower * l * m + costHigher * h * m}.sum
  }

  def cya(
    key: String,
    mainContent: Html,
    editRoutes: PartialFunction[String, WebMonad[Unit]] = Map.empty
  )(implicit extraMessages: ExtraMessages, showBackLink: Boolean = true): WebMonad[Unit] = {

    def getMapping(path: List[String]) = {
      text.verifying("error.radio-form.choose-option", a => ("DONE" :: path).contains(a))
    }

    for {
      path    <- getPath
      r  <- formPage(key)(getMapping(path), None) { (path, form, r) =>
        implicit val request: Request[AnyContent] = r
        uniform.cya(key, form, path, mainContent)
      }.flatMap {
        case "DONE" => ().pure[WebMonad]
        case x if editRoutes.isDefinedAt(x) => clear(key) >> editRoutes.apply(x)
        case x     => clear(key) >> resultToWebMonad[Unit](Redirect(x))
      }
    } yield { r }
  }

  def checkYourReturnAnswers(
    key: String,
    sdilReturn: SdilReturn,
    broughtForward: BigDecimal,
    subscription: RetrievedSubscription,
    variation: Option[ReturnsVariation] = None,
    alternativeRoutes: PartialFunction[String, WebMonad[Unit]] = Map.empty,
    originalReturn: Option[SdilReturn] = None
  )(implicit extraMessages: ExtraMessages, showBackLink: Boolean = true): WebMonad[Unit] = {

    val data = returnAmount(sdilReturn, subscription.activity.smallProducer)
    val subtotal = calculateSubtotal(data)
    val total: BigDecimal = subtotal - broughtForward

    val inner = uniform.fragments.returnsCYA(
      key,
      data,
      costLower,
      costHigher,
      subtotal,
      broughtForward,
      total,
      variation,
      subscription,
      originalReturn)

    cya(key, inner,
        {
          case "exemptions-for-small-producers" =>
            write[Boolean]("_editSmallProducers", true) >>
              clear(key) >>
              resultToWebMonad[Unit](Redirect("exemptions-for-small-producers"))
        })
  }

  def checkReturnChanges(key: String, variation: ReturnVariationData) = {
    val inner = uniform.fragments.returnVariationDifferences(key, variation)
    cya(key, inner,
      {
        case "exemptions-for-small-producers" =>
          write[Boolean]("_editSmallProducers", true) >>
            clear(key) >>
            resultToWebMonad[Unit](Redirect("exemptions-for-small-producers"))
      })
  }

  protected def askEnum[E <: EnumEntry](
    id: String,
    e: Enum[E],
    default: Option[E] = None
  ): WebMonad[E] = askOneOf(id, e.values.toList, default)

  protected def askOneOf[A](
    id: String,
    possValues: List[A],
    default: Option[A] = None,
    configOverride: JourneyConfig => JourneyConfig = identity
  )(implicit extraMessages: ExtraMessages): WebMonad[A] = {
    val valueMap: Map[String,A] =
      possValues.map{a => (a.toString, a)}.toMap
    formPage(id)(
      oneOf(possValues.map{_.toString}, "error.radio-form.choose-option"),
      default.map{_.toString}
    ) { (path, b, r) =>
      implicit val request: Request[AnyContent] = r
      val inner = uniform.fragments.radiolist(id, b, possValues.map(_.toString))
      uniform.ask(id, b, inner, path)
    }.imap(valueMap(_))(_.toString)
  }

  protected def askSet[E](
                                  id: String,
                                  possibleValues: Set[E],
                                  minSize: Int = 0,
                                  default: Option[Set[E]] = None
                                          ): WebMonad[Set[E]] = {
    val valueMap: Map[String,E] =
      possibleValues.map{a => (a.toString, a)}.toMap
    formPage(id)(
      list(nonEmptyText)
        .verifying(_.toSet subsetOf possibleValues.map(_.toString))
        .verifying("error.radio-form.choose-one-option", _.size >= minSize),
      default.map{_.map{_.toString}.toList}
    ) { (path, form, r) =>
      implicit val request: Request[AnyContent] = r
      val innerHtml = uniform.fragments.checkboxes(id, form, valueMap.keys.toList)
      uniform.ask(id, form, innerHtml, path)
    }.imap(_.map {valueMap(_)}.toSet)(_.map(_.toString).toList)
  }

  implicit class RichMapping[A](mapping: Mapping[A]) {
    def nonEmpty(errorMsg: String)(implicit m: Monoid[A], eq: Eq[A]): Mapping[A] =
      mapping.verifying(errorMsg, {!_.isEmpty})

    def nonEmpty(implicit m: Monoid[A], eq: Eq[A]): Mapping[A] = nonEmpty("error.empty")
  }


  implicit def optFormatter[A](implicit innerFormatter: Format[A]): Format[Option[A]] =
    new Format[Option[A]] {
      def reads(json: JsValue): JsResult[Option[A]] = json match {
        case JsNull => JsSuccess(none[A])
        case a      => innerFormatter.reads(a).map{_.some}
      }
      def writes(o: Option[A]): JsValue =
        o.map{innerFormatter.writes}.getOrElse(JsNull)
    }


  def askOption[T](innerMapping: Mapping[T], key: String, default: Option[Option[T]] = None)(implicit htmlForm: FormHtml[T], fmt: Format[T], showBackLink: Boolean = true): WebMonad[Option[T]] = {
    import uk.gov.voa.play.form.ConditionalMappings.mandatoryIfTrue

    val outerMapping: Mapping[Option[T]] = mapping(
      "outer" -> bool,
      "inner" -> mandatoryIfTrue(s"${key}.outer", innerMapping)
    ){(_,_) match { case (outer, inner) => inner }
    }( a => (a.isDefined, a).some )

    formPage(key)(outerMapping, default) { (path, form, r) =>
      implicit val request: Request[AnyContent] = r

      val innerForm: Form[T] = Form(single { key -> single { "inner" -> innerMapping }})
      val innerFormBound = if (form.data.get(s"$key.outer") != Some("true")) innerForm else innerForm.bind(form.data)

      val innerHead = views.html.uniform.fragments.innerhead(key)
      val innerHtml = htmlForm.asHtmlForm(key + ".inner", innerFormBound)

      val outerHtml = {
        views.html.softdrinksindustrylevy.helpers.inlineRadioButtonWithConditionalContent(
          form(s"${key}.outer"),
          Seq(
            "true" -> (("Yes", Some("hiddenTarget"))),
            "false" -> (("No", None))
          ),
          Some(innerHead |+| innerHtml),
          '_labelClass -> "block-label",
          '_labelAfter -> true,
          '_groupClass -> "form-field-group inline",
          '_dataTargetTrue -> s"anything",
          '_legendClass -> "visuallyhidden"
        )
      }

      uniform.ask(key, form, outerHtml, path)
    }
  }

  def askEmptyOption[T](
    innerMapping: Mapping[T],
    key: String,
    default: Option[T] = None
  )(implicit htmlForm: FormHtml[T],
    fmt: Format[T],
    mon: Monoid[T],
    showBackLink: Boolean = true
  ): WebMonad[T] = {
    val monDefault: Option[Option[T]] = default.map{_.some.filter{_ != mon.empty}}
    askOption[T](innerMapping, key, monDefault)
      .map{_.getOrElse(mon.empty)}
  }

  def askWithConfigOverride[T](
    mapping: Mapping[T],
    key: String,
    default: Option[T] = None,
    configOverride: JourneyConfig => JourneyConfig
  )(implicit htmlForm: FormHtml[T], fmt: Format[T], extraMessages: ExtraMessages): WebMonad[T] =
    formPage(key)(mapping, default, configOverride) { (path, form, r) =>
      implicit val request: Request[AnyContent] = r
      uniform.ask(key, form, htmlForm.asHtmlForm(key, form), path)
    }

  def ask[T](
    mapping: Mapping[T],
    key: String,
    default: Option[T] = None
  )(implicit htmlForm: FormHtml[T], fmt: Format[T], extraMessages: ExtraMessages, showBackLink: Boolean = true): WebMonad[T] =
    formPage(key)(mapping, default) { (path, form, r) =>
      implicit val request: Request[AnyContent] = r
      uniform.ask(key, form, htmlForm.asHtmlForm(key, form), path)
    }

  def ask[T](
    mapping: Mapping[T],
    key: String
  )(implicit htmlForm: FormHtml[T], fmt: Format[T], extraMessages: ExtraMessages, showBackLink: Boolean): WebMonad[T] =
    ask(mapping, key, None)

  def ask[T](
    mapping: Mapping[T],
    key: String,
    default: T
  )(implicit htmlForm: FormHtml[T], fmt: Format[T], extraMessages: ExtraMessages, showBacklinks: Boolean): WebMonad[T] =
    ask(mapping, key, default.some)(implicitly, implicitly, implicitly, showBacklinks)

  // Because I decided earlier on to make everything based off of JSON
  // I have to write silly things like this.
  implicit val formatUnit: Format[Unit] = new Format[Unit] {
    def writes(u: Unit) = JsNull
    def reads(v: JsValue) = JsSuccess(())
  }

  protected def tell[A: HtmlShow](
    id: String,
    a: A 
  )(implicit extraMessages: ExtraMessages): WebMonad[Unit] = {

    val unitMapping: Mapping[Unit] = Forms.of[Unit](new Formatter[Unit] {
      override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Unit] = Right(())

      override def unbind(key: String, value: Unit): Map[String, String] = Map.empty
    })

    formPage(id)(unitMapping, none[Unit]){  (path, form, r) =>
      implicit val request: Request[AnyContent] = r

      uniform.tell(id, form, path, a.showHtml)
    }
  }

  protected def askList[A](
    innerMapping: Mapping[A],
    id: String,
    min: Int = 0,
    max: Int = 100,
    default: List[A] = List.empty[A]
  )(implicit hs: HtmlShow[A], htmlForm: FormHtml[A], format: Format[A]): WebMonad[List[A]] =
    manyT[A](id, ask(innerMapping, _), min, max, default)

  protected def askBigText(
    id: String,
    default: Option[String] = None,
    constraints: List[(String, String => Boolean)] = Nil,
    errorOnEmpty: String = "error.required"
  )(implicit extraMessages: ExtraMessages) = {

    def constraintMap[T](
      constraints: List[(String, T => Boolean)]
    ): List[Constraint[T]] =
      constraints.map{ case (error, pred) =>
        Constraint { t: T =>
          if (pred(t)) Valid else Invalid(Seq(ValidationError(error)))
        }
      }

    val mapping = text.verifying(errorOnEmpty, _.trim.nonEmpty).verifying(constraintMap(constraints) :_*)

    formPage(id)(mapping, default) { (path, b, r) =>
      implicit val request: Request[AnyContent] = r
      val fragment = uniform.fragments.bigtext(id, b)(implicitly, extraMessages)
      uniform.ask(id,  b, fragment, path)
    }
  }

  protected def journeyEnd(
    id: String,
    now: LocalDate = LocalDate.now,
    subheading: Option[Html] = None,
    whatHappensNext: Option[Html] = None,
    getTotal: Option[Html] = None
  ): WebMonad[Result] =
    webMonad{ (rid, request, path, db) =>
      implicit val r = request

      Future.successful {
        (id.some, path, db, Ok(uniform.journeyEnd(id, path, now, subheading, whatHappensNext, getTotal)).asLeft[Result])
      }
    }

  protected def end[A : HtmlShow, R](id: String, input: A): WebMonad[R] =
    webMonad{ (rid, request, path, db) =>
      implicit val r = request

      Future.successful {
        (id.some, path, db, Ok(uniform.end(id, path, input.showHtml)).asLeft[R])
      }
    }

  protected def manyT[A](
    id: String,
    wm: String => WebMonad[A],
    min: Int = 0,
    max: Int = 100,
    default: List[A] = List.empty[A],
    editSingleForm: Option[(Mapping[A], FormHtml[A])] = None,
    configOverride: JourneyConfig => JourneyConfig = identity
  )(implicit hs: HtmlShow[A], format: Format[A], showBackLink: Boolean = true): WebMonad[List[A]] = {
    def outf(x: Option[String]): Control = x match {
      case Some("Add") => Add
      case Some("Done") => Done
      case Some(a) if a.startsWith("Delete") => Delete(a.split("\\.").last.toInt)
      case Some(b) if b.startsWith("Edit") => Edit(b.split("\\.").last.toInt)
    }
    def inf(x: Control): Option[String] = Some(x.toString)

    def confirmation(q: A): WebMonad[Boolean] =
      tell(s"remove-$id", q).map{_ => true}

    def edit(q: A): WebMonad[A] = {
      editSingleForm.fold(NotFound: WebMonad[A]) { x =>
        ask(x._1, s"edit-$id", q)(x._2, implicitly, implicitly, implicitly)
      }
    }

    many[A](id, min, max, default, confirmation, Some(edit)){ case (iid, minA, maxA, items) =>

      val mapping = optional(text) // N.b. ideally this would just be 'text' but sadly text triggers the default play "required" message for 'text'
        .verifying("error.radio-form.choose-option", a => a.nonEmpty)
        .verifying(s"$id.error.items.tooFew", a => !a.contains("Done")  || items.size >= min)
        .verifying(s"$id.error.items.tooMany", a => !a.contains("Add") || items.size < max)

      formPage(id)(mapping, None, configOverride) { (path, b, r) =>
        implicit val request: Request[AnyContent] = r
        uniform.many(id, b, items.map{_.showHtml}, path, min, editSingleForm.nonEmpty)
      }.imap(outf)(inf)
    }(wm)
  }

  def askPackSites(existingSites: List[Site]): WebMonad[List[Site]] =
    manyT("production-sites",
      ask(packagingSiteMapping,_)(packagingSiteForm, implicitly, implicitly, implicitly),
      default = existingSites,
      min = 1,
      editSingleForm = Some((packagingSiteMapping, packagingSiteForm))
    )

  def askRegDate: WebMonad[LocalDate] = {
    ask(
      startDate
        .verifying(
          "error.start-date.in-future",
          !_.isAfter(LocalDate.now)
        ).verifying(
        "error.start-date.before-tax-start",
        !_.isBefore(LocalDate.of(2018, 4, 6))),
      "start-date"
    )
  }

  def askWarehouses(sites: List[Site]): WebMonad[List[Site]] = {
    manyT("secondary-warehouses",
      ask(warehouseSiteMapping,_)(warehouseSiteForm, implicitly, implicitly, implicitly),
      default = sites,
      editSingleForm = Some((warehouseSiteMapping, warehouseSiteForm)))
  }

  implicit val address: Format[SmallProducer] = Json.format[SmallProducer]

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

  // TODO: At present this uses an Await.result to check the small producer status, thus
  // blocking a thread. At a later date uniform should be updated to include the capability
  // for a subsequent stage to invalidate a prior one.
  // TODO - also needs small producer status check scoping to the return period
  implicit def smallProducer(origSdilRef: String, sdilConnector: SoftDrinksIndustryLevyConnector)(implicit hc: HeaderCarrier): Mapping[SmallProducer] = mapping(
    "alias" -> optional(text),
    "sdilRef" -> text
      .verifying(
        "error.sdilref.invalid", x => {
          x.nonEmpty ||
            x.matches("^X[A-Z]SDIL000[0-9]{6}$") &&
              isCheckCorrect(x, 1)
        })
      .verifying("error.sdilref.notSmall", x => {
          Await.result(isSmallProducer(x, sdilConnector: SoftDrinksIndustryLevyConnector), 20.seconds)
        })
      .verifying("error.sdilref.same", x => {
        x != origSdilRef
      }),
    "lower"   -> litreage,
    "higher"  -> litreage
  ){
    (alias, ref,l,h) => SmallProducer(alias, ref, (l,h))
  }{
    case SmallProducer(alias, ref, (l,h)) => (alias, ref,l,h).some
  }


  def isSmallProducer(sdilRef: String, sdilConnector: SoftDrinksIndustryLevyConnector)(implicit hc: HeaderCarrier): Future[Boolean] =
    sdilConnector.retrieveSubscription(sdilRef).flatMap {
      case Some(x) => x.activity.smallProducer
      case None    => false
    }(mdcExecutionContext)

  implicit val litreageForm = new FormHtml[(Long,Long)] {
    def asHtmlForm(key: String, form: Form[(Long,Long)])(implicit messages: Messages): Html = {
      uniform.fragments.litreage(key, form, false)(messages)
    }
  }

}

