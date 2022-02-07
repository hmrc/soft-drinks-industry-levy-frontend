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

package views.html.uniform.fragments

import _root_.play.twirl.api.TwirlFeatureImports._
import _root_.play.twirl.api.TwirlHelperImports._
import _root_.play.twirl.api.Html
import _root_.play.twirl.api.JavaScript
import _root_.play.twirl.api.Txt
import _root_.play.twirl.api.Xml
import models._
import controllers._
import play.api.i18n._
import views.html._
import play.api.templates.PlayMagic._
import play.api.mvc._
import play.api.data._
import sdil.utility._
import sdil.uniform.AdaptMessages.ufMessagesToPlayMessages
import ltbs.uniform._
/*17.2*/
import sdil.models.ReturnPeriod
/*18.2*/
import sdil.models.ReturnsVariation
/*19.2*/
import views.html.softdrinksindustrylevy.helpers._
/*20.2*/
import sdil.models.retrieved.RetrievedSubscription

object returnsPaymentsBlurbNoClaim
    extends _root_.play.twirl.api.BaseScalaTemplate[
      play.twirl.api.HtmlFormat.Appendable,
      _root_.play.twirl.api.Format[play.twirl.api.HtmlFormat.Appendable]](play.twirl.api.HtmlFormat)
    with _root_.play.twirl.api.Template12[
      RetrievedSubscription,
      ReturnPeriod,
      String,
      BigDecimal,
      String,
      ReturnsVariation,
      List[scala.Tuple3[String, scala.Tuple2[Long, Long], Int]],
      BigDecimal,
      BigDecimal,
      BigDecimal,
      BigDecimal,
      Messages,
      play.twirl.api.HtmlFormat.Appendable] {

  /**/
  def apply /*22.2*/ (
    subscription: RetrievedSubscription,
    paymentDate: ReturnPeriod,
    sdilRef: String,
    total: BigDecimal,
    formattedTotal: String,
    variation: ReturnsVariation,
    lineItems: List[(String, (Long, Long), Int)],
    costLower: BigDecimal,
    costHigher: BigDecimal,
    subtotal: BigDecimal,
    broughtForward: BigDecimal)(implicit messages: Messages): play.twirl.api.HtmlFormat.Appendable =
    _display_ {
      {

        Seq[Any](
          format.raw /*23.1*/ ("""
"""),
          _display_( /*24.2*/ if (total > 0) /*24.15*/ {
            _display_(
              Seq[Any](
                format.raw /*24.17*/ ("""
"""),
                format.raw /*25.5*/ ("""<p>"""),
                _display_( /*25.9*/ Html(
                    messages(
                      "return-sent.paymentsBlurb.payby",
                      formattedTotal,
                      paymentDate.deadline.format("dd MMMM yyyy")))),
                format.raw /*25.119*/ ("""</p>

<p>"""),
                _display_( /*27.9*/ Html(messages("return-sent.paymentsBlurb.payusing", sdilRef))),
                format.raw /*27.70*/ ("""</p>
""")
              ))
          }),
          format.raw /*28.2*/ ("""

"""),
          _display_( /*30.2*/ if (total == 0) /*30.16*/ {
            _display_(
              Seq[Any](
                format.raw /*30.18*/ ("""
"""),
                format.raw /*31.5*/ ("""<p>"""),
                _display_( /*31.9*/ messages("return-sent.paymentsBlurb.noReturn.p1")),
                format.raw /*31.58*/ ("""</p>
""")
              ))
          }),
          format.raw /*32.2*/ ("""

"""),
          _display_( /*34.2*/ if (total < 0) /*34.15*/ {
            _display_(
              Seq[Any](
                format.raw /*34.17*/ ("""
"""),
                format.raw /*35.5*/ ("""<p>"""),
                _display_( /*35.9*/ messages("return-sent.paymentsBlurb.noReturn.p1")),
                format.raw /*35.58*/ ("""</p>
<p>"""),
                _display_( /*36.9*/ messages("return-sent.paymentsBlurb.credit.p1")),
                format.raw /*36.56*/ ("""</p>
""")
              ))
          }),
          format.raw /*37.2*/ ("""

"""),
          format.raw /*39.1*/ ("""<p>
    """),
          _display_( /*40.6*/ messages(
              "return-sent.paymentsBlurb.nextReturn",
              paymentDate.next.start.format("MMMM"),
              paymentDate.next.end.format("MMMM yyyy"),
              paymentDate.next.deadline.format("dd MMMM yyyy")
            )),
          format.raw /*44.54*/ ("""
    """),
          format.raw /*45.1*/ ("""</p>

"""),
          _display_( /*47.2*/ if (variation.importer._1 || variation.packer._1) /*47.50*/ {
            _display_(
              Seq[Any](
                format.raw /*47.52*/ ("""
"""),
                format.raw /*48.5*/ ("""<p>"""),
                _display_( /*48.9*/ messages("return-sent.paymentsBlurb.variation")),
                format.raw /*48.56*/ ("""</p>
""")
              ))
          }),
          format.raw /*49.2*/ ("""

"""),
          format.raw /*51.1*/ ("""<h2 class="heading-medium">"""),
          _display_( /*51.29*/ messages("return-sent.paymentsBlurb.help-heading")),
          format.raw /*51.79*/ ("""</h2>
<p>"""),
          _display_( /*52.5*/ Html(
              messages(
                "return-sent.paymentsBlurb.help-subheading",
                sdil.controllers.routes.ServicePageController.show()))),
          format.raw /*52.118*/ ("""</p>

<ul class="list list-bullet">
    <li>"""),
          _display_( /*55.14*/ messages("return-sent.paymentsBlurb.payDetails.1")),
          format.raw /*55.64*/ ("""</li>
    <li>"""),
          _display_( /*56.14*/ messages("return-sent.paymentsBlurb.payDetails.2")),
          format.raw /*56.64*/ ("""</li>
    """),
          _display_( /*57.10*/ if (subscription.productionSites.nonEmpty) /*57.51*/ {
            _display_(
              Seq[Any](
                format.raw /*57.53*/ ("""
    """),
                format.raw /*58.13*/ ("""<li>"""),
                _display_( /*58.18*/ messages("return-sent.paymentsBlurb.payDetails.3")),
                format.raw /*58.68*/ ("""</li>
    """)
              ))
          }),
          format.raw /*59.10*/ ("""
    """),
          format.raw /*60.9*/ ("""<li>"""),
          _display_( /*60.14*/ messages("return-sent.paymentsBlurb.payDetails.4")),
          format.raw /*60.64*/ ("""</li>
</ul>

<table class="check-your-answers">
    <caption><h2>"""),
          _display_( /*64.19*/ messages("return-sent.cya-table.header")),
          format.raw /*64.59*/ ("""</h2></caption>
    <thead>
    <tr>
        <th>"""),
          _display_( /*67.18*/ messages("check-your-answers.activity")),
          format.raw /*67.57*/ ("""</th>
        <th>"""),
          _display_( /*68.18*/ messages("check-your-answers.band")),
          format.raw /*68.53*/ ("""</th>
        <th class="numeric">"""),
          _display_( /*69.34*/ messages("check-your-answers.litres")),
          format.raw /*69.71*/ ("""</th>
        <th class="numeric no-padding-right">"""),
          _display_( /*70.51*/ messages("check-your-answers.levy")),
          format.raw /*70.86*/ ("""</th>
    </tr>
    </thead>

    <tbody>
    """),
          _display_( /*75.10*/ for ((lineKey, litres, multiplier) <- lineItems) yield /*75.57*/ {
            if (lineKey.contains("claim-credits-for-exports")) {} else if (lineKey.contains(
                                                                             "claim-credits-for-lost-damaged")) {} else
              _display_(
                Seq[Any](
                  format.raw /*75.59*/ ("""
    """),
                  format.raw /*76.13*/ ("""<tr>
        <td rowspan="2">"""),
                  _display_( /*77.34*/ messages(s"check-your-answers.$lineKey")),
                  format.raw /*77.74*/ ("""</td>
        <td>"""),
                  _display_( /*78.22*/ messages(s"check-your-answers.low")),
                  format.raw /*78.57*/ ("""</td>
        <td class="numeric">"""),
                  _display_( /*79.38*/ { f"${litres._1}%,d" }),
                  format.raw /*79.58*/ ("""</td>
        <td class="no-minus-wrapping numeric">"""),
                  _display_( /*80.56*/ format_money(costLower * litres._1 * multiplier)),
                  format.raw /*80.104*/ ("""</td>
    </tr>
    <tr>
        <td>"""),
                  _display_( /*83.22*/ messages(s"check-your-answers.high")),
                  format.raw /*83.58*/ ("""</td>
        <td class="numeric">"""),
                  _display_( /*84.38*/ { f"${litres._2}%,d" }),
                  format.raw /*84.58*/ ("""</td>
        <td class="no-minus-wrapping numeric">"""),
                  _display_( /*85.56*/ format_money(costHigher * litres._2 * multiplier)),
                  format.raw /*85.105*/ ("""</td>
    </tr>
    """)
                ))
          }),
          format.raw /*87.10*/ ("""
    """),
          format.raw /*88.9*/ ("""<tr>
        <th scope="row" colspan="3"><span class="heading-small">"""),
          _display_( /*89.70*/ messages("check-your-answers.subtotal")),
          format.raw /*89.109*/ ("""</span></th>
        <td class="numeric no-padding-right"><span class="heading-small no-minus-wrapping">"""),
          _display_( /*90.97*/ format_money(subtotal)),
          format.raw /*90.119*/ ("""</span></td>
    </tr>
    <tr>
        <th scope="row" colspan="3"><span class="heading-small">"""),
          _display_( /*93.70*/ messages(s"check-your-answers.balance.brought.forward")),
          format.raw /*93.125*/ ("""</span></th>
        <td class="numeric no-padding-right"><span class="heading-small no-minus-wrapping">"""),
          _display_( /*94.97*/ format_money(-broughtForward, "+")),
          format.raw /*94.131*/ ("""</span></td>
    </tr>
    <tr>
        <th scope="row" colspan="3"><span class="heading-small">"""),
          _display_( /*97.70*/ messages(s"check-your-answers.total")),
          format.raw /*97.107*/ ("""</span></th>
        <td class="numeric no-padding-right"><span class="heading-small no-minus-wrapping">"""),
          _display_( /*98.97*/ format_money(total)),
          format.raw /*98.116*/ ("""</span></td>
    </tr>
    </tbody>
</table>
""")
        )
      }
    }

  def render(
    subscription: RetrievedSubscription,
    paymentDate: ReturnPeriod,
    sdilRef: String,
    total: BigDecimal,
    formattedTotal: String,
    variation: ReturnsVariation,
    lineItems: List[scala.Tuple3[String, scala.Tuple2[Long, Long], Int]],
    costLower: BigDecimal,
    costHigher: BigDecimal,
    subtotal: BigDecimal,
    broughtForward: BigDecimal,
    messages: Messages): play.twirl.api.HtmlFormat.Appendable =
    apply(
      subscription,
      paymentDate,
      sdilRef,
      total,
      formattedTotal,
      variation,
      lineItems,
      costLower,
      costHigher,
      subtotal,
      broughtForward)(messages)

  def f: (
    (
      RetrievedSubscription,
      ReturnPeriod,
      String,
      BigDecimal,
      String,
      ReturnsVariation,
      List[scala.Tuple3[String, scala.Tuple2[Long, Long], Int]],
      BigDecimal,
      BigDecimal,
      BigDecimal,
      BigDecimal) => (Messages) => play.twirl.api.HtmlFormat.Appendable) =
    (
      subscription,
      paymentDate,
      sdilRef,
      total,
      formattedTotal,
      variation,
      lineItems,
      costLower,
      costHigher,
      subtotal,
      broughtForward) =>
      (messages) =>
        apply(
          subscription,
          paymentDate,
          sdilRef,
          total,
          formattedTotal,
          variation,
          lineItems,
          costLower,
          costHigher,
          subtotal,
          broughtForward)(messages)

  def ref: this.type = this

}
/*
-- GENERATED --
DATE: Mon Feb 07 11:44:25 GMT 2022
SOURCE: /Users/jakereid/Documents/Services/soft-drinks-industry-levy-frontend/app/views/uniform/fragments/returnsPaymentsBlurb.scala.html
HASH: a806fce725077b5ea06121550f928699830fa404
MATRIX: 554->606|594->639|638->676|697->728|1250->781|1670->1107|1698->1109|1720->1122|1760->1124|1792->1129|1822->1133|1954->1243|1994->1257|2076->1318|2112->1324|2141->1327|2164->1341|2204->1343|2236->1348|2266->1352|2336->1401|2372->1407|2401->1410|2423->1423|2463->1425|2495->1430|2525->1434|2595->1483|2634->1496|2702->1543|2738->1549|2767->1551|2802->1560|3019->1756|3047->1757|3080->1764|3137->1812|3177->1814|3209->1819|3239->1823|3307->1870|3343->1876|3372->1878|3427->1906|3498->1956|3534->1966|3669->2079|3749->2132|3820->2182|3866->2201|3937->2251|3979->2266|4029->2307|4069->2309|4110->2322|4142->2327|4213->2377|4259->2392|4295->2401|4327->2406|4398->2456|4495->2526|4556->2566|4641->2624|4701->2663|4751->2686|4807->2721|4873->2760|4931->2797|5014->2853|5070->2888|5152->2943|5215->2990|5255->2992|5296->3005|5361->3043|5422->3083|5476->3110|5532->3145|5602->3188|5643->3208|5731->3269|5801->3317|5890->3379|5947->3415|6017->3458|6058->3478|6146->3539|6217->3588|6281->3621|6317->3630|6418->3704|6479->3743|6615->3852|6659->3874|6795->3983|6872->4038|7008->4147|7064->4181|7200->4290|7259->4327|7395->4436|7436->4455
LINES: 20->17|21->18|22->19|23->20|28->22|33->23|34->24|34->24|34->24|35->25|35->25|35->25|37->27|37->27|38->28|40->30|40->30|40->30|41->31|41->31|41->31|42->32|44->34|44->34|44->34|45->35|45->35|45->35|46->36|46->36|47->37|49->39|50->40|54->44|55->45|57->47|57->47|57->47|58->48|58->48|58->48|59->49|61->51|61->51|61->51|62->52|62->52|65->55|65->55|66->56|66->56|67->57|67->57|67->57|68->58|68->58|68->58|69->59|70->60|70->60|70->60|74->64|74->64|77->67|77->67|78->68|78->68|79->69|79->69|80->70|80->70|85->75|85->75|85->75|86->76|87->77|87->77|88->78|88->78|89->79|89->79|90->80|90->80|93->83|93->83|94->84|94->84|95->85|95->85|97->87|98->88|99->89|99->89|100->90|100->90|103->93|103->93|104->94|104->94|107->97|107->97|108->98|108->98
-- GENERATED --
 */
