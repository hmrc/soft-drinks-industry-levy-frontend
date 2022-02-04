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

package views.uniform.fragments

import _root_.play.twirl.api.Html
import play.api.i18n._
import sdil.models.retrieved.RetrievedSubscription
import sdil.models.{ReturnsVariation, SdilReturn}
import views.html.softdrinksindustrylevy.helpers._

object returnsCYACantClaim
    extends _root_.play.twirl.api.BaseScalaTemplate[
      play.twirl.api.HtmlFormat.Appendable,
      _root_.play.twirl.api.Format[play.twirl.api.HtmlFormat.Appendable]](play.twirl.api.HtmlFormat)
    with _root_.play.twirl.api.Template11[
      String,
      List[scala.Tuple3[String, scala.Tuple2[Long, Long], Int]],
      BigDecimal,
      BigDecimal,
      BigDecimal,
      BigDecimal,
      BigDecimal,
      Option[ReturnsVariation],
      RetrievedSubscription,
      Option[SdilReturn],
      Messages,
      play.twirl.api.HtmlFormat.Appendable] {

  /**/
  def apply /*22.2*/ (
    key: String,
    lineItems: List[(String, (Long, Long), Int)],
    costLower: BigDecimal,
    costHigher: BigDecimal,
    subtotal: BigDecimal,
    broughtForward: BigDecimal,
    total: BigDecimal,
    variation: Option[ReturnsVariation] = None,
    subscription: RetrievedSubscription,
    originalReturn: Option[SdilReturn])(implicit m: Messages): play.twirl.api.HtmlFormat.Appendable = {
    _display_ {
      {

        Seq[Any](
          format.raw /*23.1*/ ("""
"""),
          format.raw /*24.1*/ ("""<table class="check-your-answers">

    """),
          _display_( /*26.6*/ if (total == 0) /*26.20*/ {
            _display_(
              Seq[Any](
                format.raw /*26.22*/ ("""
        """),
                format.raw /*27.9*/ ("""<caption><h2>"""),
                _display_( /*27.23*/ Messages(s"$key.balance.nil", format_money(total))),
                format.raw /*27.73*/ ("""</h2></caption>
    """)
              ))
          } /*28.7*/
          else
            /*28.12*/ {
              _display_(
                Seq[Any](
                  format.raw /*28.13*/ ("""
        """),
                  _display_( /*29.10*/ if (total > 0) /*29.23*/ {
                    _display_(Seq[Any](
                      format.raw /*29.25*/ ("""
            """),
                      format.raw /*30.13*/ ("""<caption><h2>"""),
                      _display_( /*30.27*/ Messages(s"$key.balance.due", format_money(total))),
                      format.raw /*30.77*/ ("""</h2></caption>
        """)
                    ))
                  } /*31.11*/
                  else
                    /*31.16*/ {
                      _display_(Seq[Any](
                        format.raw /*31.17*/ ("""
            """),
                        format.raw /*32.13*/ ("""<caption><h2>"""),
                        _display_( /*32.27*/ Messages(s"$key.balance.credit", format_money(total.abs))),
                        format.raw /*32.84*/ ("""</h2></caption>
        """)
                      ))
                    }),
                  format.raw /*33.10*/ ("""
    """)
                ))
            }),
          format.raw /*34.6*/ ("""
    """),
          format.raw /*35.5*/ ("""<thead>
        <tr>
            <th scope="col" class="table-header-bold">"""),
          _display_( /*37.56*/ Messages(s"$key.activity")),
          format.raw /*37.82*/ ("""</th>
            <th scope="col" class="table-header-bold">"""),
          _display_( /*38.56*/ Messages(s"$key.band")),
          format.raw /*38.78*/ ("""</th>
            <th scope="col" class="table-header-bold numeric">"""),
          _display_( /*39.64*/ Messages(s"$key.litres")),
          format.raw /*39.88*/ ("""</th>
            <th scope="col" class="table-header-bold numeric">"""),
          _display_( /*40.64*/ Messages(s"$key.levy")),
          format.raw /*40.86*/ ("""</th>
            <td>&nbsp;</td>
        </tr>
    </thead>

    <tbody>
        """),
          _display_( /*46.10*/ for ((lineKey, litres, multiplier) <- lineItems) yield /*46.57*/ {
            if (lineKey
                  .contains("claim-credits-for-exports")) {} else if (lineKey.contains(
                                                                        "claim-credits-for-lost-damaged")) {} else {
              _display_(
                Seq[Any](
                  format.raw /*46.59*/ ("""
            """),
                  format.raw /*47.13*/ ("""<tr>
                <td rowspan="2">"""),
                  _display_( /*48.34*/ Messages(s"$key.$lineKey")),
                  format.raw /*48.60*/ ("""</td>
                <td>"""),
                  _display_( /*49.22*/ Messages(s"$key.low")),
                  format.raw /*49.43*/ ("""</td>
                <td class="numeric">"""),
                  _display_( /*50.38*/ {
                    f"${litres._1}%,d"
                  }),
                  format.raw /*50.58*/ ("""</td>
                <td class="no-minus-wrapping numeric">"""),
                  _display_( /*51.56*/ format_money(costLower * litres._1 * multiplier)),
                  format.raw /*51.104*/ ("""</td>
                <td class="change-answer">
                    <a href='"""),
                  _display_( /*53.31*/ lineKey),
                  format.raw /*53.38*/ ("""'>
                    """),
                  _display_( /*54.22*/ Messages(s"$key.change")),
                  format.raw /*54.46*/ ("""
                    """),
                  format.raw /*55.21*/ ("""</a>
                </td>
            </tr>
            <tr>
                <td>"""),
                  _display_( /*59.22*/ Messages(s"$key.high")),
                  format.raw /*59.44*/ ("""</td>
                <td class="numeric">"""),
                  _display_( /*60.38*/ {
                    f"${litres._2}%,d"
                  }),
                  format.raw /*60.58*/ ("""</td>
                <td class="no-minus-wrapping numeric"> """),
                  _display_( /*61.57*/ format_money(costHigher * litres._2 * multiplier)),
                  format.raw /*61.106*/ ("""</td>
                <td class="change-answer">
                    <a href='"""),
                  _display_( /*63.31*/ lineKey),
                  format.raw /*63.38*/ ("""'>
                    """),
                  _display_( /*64.22*/ Messages(s"$key.change")),
                  format.raw /*64.46*/ ("""
                    """),
                  format.raw /*65.21*/ ("""</a>
                </td>
            </tr>
        """)
                ))
            }
          }),
          format.raw /*68.10*/ ("""
        """),
          _display_( /*69.10*/ if (originalReturn.isEmpty) /*69.36*/ {
            _display_(
              Seq[Any](
                format.raw /*69.38*/ ("""
        """),
                format.raw /*70.9*/ ("""<tr>
            <th scope="row" colspan="3"><span class="heading-small">"""),
                _display_( /*71.70*/ Messages(s"$key.subtotal")),
                format.raw /*71.96*/ ("""</span></th>
            <td class="numeric"><span class="heading-small no-minus-wrapping numeric">"""),
                _display_( /*72.88*/ format_money(subtotal)),
                format.raw /*72.110*/ ("""</span></td>
        </tr>
        <tr>
            <th scope="row" colspan="3"><span class="heading-small">"""),
                _display_( /*75.70*/ Messages(s"$key.balance.brought.forward")),
                format.raw /*75.111*/ ("""</span></th>
            <td class="numeric"><span class="heading-small no-minus-wrapping numeric">"""),
                _display_( /*76.88*/ format_money(-broughtForward, "+")),
                format.raw /*76.122*/ ("""</span></td>
        </tr>
        """)
              ))
          }),
          format.raw /*78.10*/ ("""
        """),
          _display_( /*79.10*/ if (originalReturn.nonEmpty) /*79.37*/ {
            _display_(
              Seq[Any](
                format.raw /*79.39*/ ("""
            """),
                _display_( /*80.14*/ originalReturn /*80.28*/ .map /*80.32*/ {
                  r =>
                    _display_(Seq[Any](
                      format.raw /*80.39*/ ("""

                """),
                      format.raw /*82.17*/ ("""<tr>
                    <th scope="row" colspan="3">"""),
                      _display_( /*83.50*/ Messages(s"$key.total.original")),
                      format.raw /*83.82*/ ("""</th>
                    <td class="numeric">"""),
                      _display_( /*84.42*/ format_money(r.total)),
                      format.raw /*84.63*/ ("""</td>
                </tr>
                <tr>
                    <th scope="row" colspan="3">"""),
                      _display_( /*87.50*/ Messages(s"$key.total.new")),
                      format.raw /*87.77*/ ("""</th>
                    <td class="numeric">"""),
                      _display_( /*88.42*/ format_money(subtotal)),
                      format.raw /*88.64*/ ("""</td>
                </tr>
                <tr>
                    <th scope="row" colspan="3">"""),
                      _display_( /*91.50*/ Messages(s"$key.total.balance")),
                      format.raw /*91.81*/ ("""</th>
                    <td class="numeric">"""),
                      _display_( /*92.42*/ format_money(-broughtForward, "+")),
                      format.raw /*92.76*/ ("""</td>
                </tr>
                <tr>
                    """),
                      _display_( /*95.22*/ if ((total - r.total) < 0) /*95.47*/ {
                        _display_(Seq[Any](
                          format.raw /*95.49*/ ("""
                        """),
                          format.raw /*96.25*/ ("""<th scope="row" colspan="3"><span class="heading-small">"""),
                          _display_( /*96.82*/ Messages(s"$key.net.adjusted.negative")),
                          format.raw /*96.121*/ ("""</span></th>
                    """)
                        ))
                      } /*97.23*/
                      else
                        /*97.28*/ {
                          _display_(Seq[Any](
                            format.raw /*97.29*/ ("""
                        """),
                            format.raw /*98.25*/ ("""<th scope="row" colspan="3"><span class="heading-small">"""),
                            _display_( /*98.82*/ Messages(s"$key.net.adjusted.positive")),
                            format.raw /*98.121*/ ("""</span></th>
                    """)
                          ))
                        }),
                      format.raw /*99.22*/ ("""
                    """),
                      format.raw /*100.21*/ ("""<td class="numeric"><span class="heading-small no-minus-wrapping">"""),
                      _display_( /*100.88*/ format_money(total - r.total)),
                      format.raw /*100.117*/ ("""</span></td>
                </tr>
            """)
                    ))
                }),
                format.raw /*102.14*/ ("""

        """)
              ))
          } /*104.11*/
          else
            /*104.16*/ {
              _display_(
                Seq[Any](
                  format.raw /*104.17*/ ("""
            """),
                  format.raw /*105.13*/ ("""<tr>
                <th scope="row" colspan="3"><span class="heading-small">"""),
                  _display_( /*106.74*/ Messages(s"$key.total")),
                  format.raw /*106.97*/ ("""</span></th>
                <td class="numeric"><span class="heading-small no-minus-wrapping numeric">"""),
                  _display_( /*107.92*/ format_money(total)),
                  format.raw /*107.111*/ ("""</span></td>
            </tr>
        """)
                ))
            }),
          format.raw /*109.10*/ ("""
    """),
          format.raw /*110.5*/ ("""</tbody>
</table>


"""),
          _display_( /*114.2*/ originalReturn /*114.16*/ .map /*114.20*/ { r =>
            _display_(
              Seq[Any](
                format.raw /*114.27*/ ("""
    """),
                _display_( /*115.6*/ if ((total - r.total) > 0) /*115.31*/ {
                  _display_(Seq[Any](
                    format.raw /*115.33*/ ("""
        """),
                    format.raw /*116.9*/ ("""<br/>
        <div class="panel panel-border-wide form-group">
            <p>
                """),
                    _display_( /*119.18*/ Html(Messages(s"$key.interest.help"))),
                    format.raw /*119.55*/ ("""
            """),
                    format.raw /*120.13*/ ("""</p>
        </div>
    """)
                  ))
                } /*122.7*/
                else
                  /*122.12*/ {
                    _display_(
                      Seq[Any](
                        format.raw /*122.13*/ ("""
        """),
                        format.raw /*123.9*/ ("""<br/><br/>
    """)))
                  }),
                format.raw /*124.6*/ ("""
""")
              ))
          }),
          format.raw /*125.2*/ ("""

    """),
          _display_( /*127.6*/ variation /*127.15*/ .map /*127.19*/ { variation =>
            _display_(
              Seq[Any](
                format.raw /*127.34*/ ("""

        """),
                _display_( /*129.10*/ if (variation.packingSites.nonEmpty) /*129.45*/ {
                  _display_(Seq[Any](
                    format.raw /*129.47*/ ("""

            """),
                    format.raw /*131.13*/ ("""<table class="check-your-answers">
                <caption><h2>"""),
                    _display_( /*132.31*/ Messages("variations.cya.packsites.add")),
                    format.raw /*132.71*/ ("""</h2></caption>

                <thead>
                    <tr>
                        <th class="cya-width">
                            """),
                    _display_( /*137.30*/ Messages("variations.cya.packsites.address")),
                    format.raw /*137.74*/ ("""
                        """),
                    format.raw /*138.25*/ ("""</th>
                        <th colspan="2">
                            """),
                    _display_( /*140.30*/ Messages("variations.cya.postcode")),
                    format.raw /*140.65*/ ("""
                        """),
                    format.raw /*141.25*/ ("""</th>
                    </tr>
                </thead>

                <tbody>
                """),
                    _display_( /*146.18*/ for (site <- variation.packingSites) yield /*146.53*/ {
                      _display_(Seq[Any](
                        format.raw /*146.55*/ ("""
                    """),
                        format.raw /*147.21*/ ("""<tr>
                        <td>
                            <details role="group">
                                <summary aria-controls="details-content-1" aria-expanded="false">
                                    <span class="summary">"""),
                        _display_( /*151.60*/ site /*151.64*/ .address.lines.head),
                        format.raw /*151.83*/ ("""</span>
                                </summary>
                                """),
                        _display_( /*153.34*/ for (line <- site.address.lines.tail) yield /*153.70*/ {
                          _display_(Seq[Any](
                            format.raw /*153.72*/ ("""
                                    """),
                            format.raw /*154.37*/ ("""<div aria-hidden="true">"""),
                            _display_( /*154.62*/ line),
                            format.raw /*154.66*/ ("""</div>
                                """)
                          ))
                        }),
                        format.raw /*155.34*/ ("""
                                """),
                        format.raw /*156.33*/ ("""<div class="postal-code">"""),
                        _display_( /*156.59*/ site /*156.63*/ .address.postCode),
                        format.raw /*156.80*/ ("""</div>
                            </details>
                        </td>
                        <td>"""),
                        _display_( /*159.30*/ site /*159.34*/ .address.postCode),
                        format.raw /*159.51*/ ("""</td>
                        <td class="change-answer">
                            <a href="production-site-details">
                                <p class="edit-change-link">"""),
                        _display_( /*162.62*/ Html(Messages("variations.cya.packsites.change"))),
                        format.raw /*162.111*/ ("""</p>
                            </a>
                        </td>
                    </tr>
                """)
                      ))
                    }),
                    format.raw /*166.18*/ ("""
                """),
                    format.raw /*167.17*/ ("""</tbody>
            </table>
        """)
                  ))
                }),
                format.raw /*169.10*/ ("""

        """),
                _display_( /*171.10*/ if (variation.warehouses.nonEmpty) /*171.43*/ {
                  _display_(Seq[Any](
                    format.raw /*171.45*/ ("""
            """),
                    format.raw /*172.13*/ ("""<table class="check-your-answers">
                <caption><h2>"""),
                    _display_( /*173.31*/ Messages("variations.cya.warehouses.add")),
                    format.raw /*173.72*/ ("""</h2></caption>

                <thead>
                    <tr>
                        <th class="cya-width">
                            """),
                    _display_( /*178.30*/ Messages("variations.cya.warehouses")),
                    format.raw /*178.67*/ ("""
                        """),
                    format.raw /*179.25*/ ("""</th>
                        <th colspan="2">
                            """),
                    _display_( /*181.30*/ Messages("variations.cya.postcode")),
                    format.raw /*181.65*/ ("""
                        """),
                    format.raw /*182.25*/ ("""</th>
                    </tr>
                </thead>

                <tbody>
                """),
                    _display_( /*187.18*/ for (site <- variation.warehouses) yield /*187.51*/ {
                      _display_(Seq[Any](
                        format.raw /*187.53*/ ("""
                    """),
                        format.raw /*188.21*/ ("""<tr>
                        <td>
                            <details role="group">
                                <summary aria-controls="details-content-1" aria-expanded="false">
                                    <span class="summary">
                                    """),
                        _display_( /*193.38*/ if (site.getLines.head.nonEmpty) /*193.69*/ {
                          _display_(_display_( /*193.72*/ site /*193.76*/ .getLines.head))
                        } /*193.92*/
                        else /*193.97*/ { _display_(_display_( /*193.99*/ site /*193.103*/ .getLines.tail.head)) }),
                        format.raw /*193.123*/ ("""
                                    """),
                        format.raw /*194.37*/ ("""</span>
                                </summary>
                                """),
                        _display_( /*196.34*/ for (line <- site.getLines.tail.dropRight(1)) yield /*196.78*/ {
                          _display_(Seq[Any](
                            format.raw /*196.80*/ ("""
                                    """),
                            format.raw /*197.37*/ ("""<div aria-hidden="true">"""),
                            _display_( /*197.62*/ line),
                            format.raw /*197.66*/ ("""</div>
                                """)
                          ))
                        }),
                        format.raw /*198.34*/ ("""
                                """),
                        format.raw /*199.33*/ ("""<div class="postal-code">"""),
                        _display_( /*199.59*/ site /*199.63*/ .address.postCode),
                        format.raw /*199.80*/ ("""</div>
                            </details>
                        </td>
                        <td>"""),
                        _display_( /*202.30*/ site /*202.34*/ .address.postCode),
                        format.raw /*202.51*/ ("""</td>
                        <td class="change-answer">
                            <a href="secondary-warehouse-details">
                                <p class="edit-change-link">"""),
                        _display_( /*205.62*/ Html(Messages("variations.cya.warehouses.change"))),
                        format.raw /*205.112*/ ("""</p>
                            </a>
                        </td>
                    </tr>
                """)
                      ))
                    }),
                    format.raw /*209.18*/ ("""
                """),
                    format.raw /*210.17*/ ("""</tbody>
            </table>
        """)
                  ))
                }),
                format.raw /*212.10*/ ("""

        """),
                _display_( /*214.10*/ if (variation.importer._1 || variation.packer._1) /*214.58*/ {
                  _display_(Seq[Any](
                    format.raw /*214.60*/ ("""
            """),
                    format.raw /*215.13*/ ("""<h2 class="heading-medium">"""),
                    _display_( /*215.41*/ Messages(s"$key.variations.subheading")),
                    format.raw /*215.80*/ ("""</h2>
        """)
                  ))
                }),
                format.raw /*216.10*/ ("""

        """),
                _display_( /*218.10*/ if (variation.importer._1 && subscription.warehouseSites.isEmpty && variation.warehouses.nonEmpty)
                  /*218.107*/ {
                    _display_(Seq[Any](
                      format.raw /*218.109*/ ("""
            """),
                      format.raw /*219.13*/ ("""<p>"""),
                      _display_( /*219.17*/ Html(
                        Messages("check-your-answers.variations.import-with-warehouses-some"))),
                      format.raw /*219.92*/ ("""</p>
        """)
                    ))
                  }),
                format.raw /*220.10*/ ("""

        """),
                _display_( /*222.10*/ if (variation.importer._1 && subscription.warehouseSites.isEmpty && variation.warehouses.isEmpty)
                  /*222.106*/ {
                    _display_(Seq[Any](
                      format.raw /*222.108*/ ("""
            """),
                      format.raw /*223.13*/ ("""<p>"""),
                      _display_( /*223.17*/ Html(
                        Messages("check-your-answers.variations.import-with-warehouses-empty"))),
                      format.raw /*223.93*/ ("""</p>
        """)
                    ))
                  }),
                format.raw /*224.10*/ ("""

        """),
                _display_( /*226.10*/ if (variation.importer._1 && subscription.warehouseSites.nonEmpty) /*226.75*/ {
                  _display_(Seq[Any](
                    format.raw /*226.77*/ ("""
            """),
                    format.raw /*227.13*/ ("""<p>"""),
                    _display_( /*227.17*/ Messages("check-your-answers.variations.import-without-warehouses")),
                    format.raw /*227.84*/ ("""</p>
        """)
                  ))
                }),
                format.raw /*228.10*/ ("""

        """),
                _display_( /*230.10*/ if (variation.packer._1 && subscription.productionSites.isEmpty) /*230.73*/ {
                  _display_(Seq[Any](
                    format.raw /*230.75*/ ("""
            """),
                    format.raw /*231.13*/ ("""<p>"""),
                    _display_( /*231.17*/ Html(Messages("check-your-answers.variations.import-with-packSites"))),
                    format.raw /*231.86*/ ("""</p>
        """)
                  ))
                }),
                format.raw /*232.10*/ ("""

        """),
                _display_( /*234.10*/ if (variation.packer._1 && subscription.productionSites.nonEmpty) /*234.74*/ {
                  _display_(Seq[Any](
                    format.raw /*234.76*/ ("""
            """),
                    format.raw /*235.13*/ ("""<p>"""),
                    _display_( /*235.17*/ Html(Messages("check-your-answers.variations.import-without-packSites"))),
                    format.raw /*235.89*/ ("""</p>
        """)
                  ))
                }),
                format.raw /*236.10*/ ("""
    """)
              ))
          }),
          format.raw /*237.6*/ ("""

"""),
          _display_( /*239.2*/ if (originalReturn.isEmpty) /*239.28*/ {
            _display_(
              Seq[Any](
                format.raw /*239.30*/ ("""
    """),
                format.raw /*240.5*/ ("""<h2 class="heading-medium">"""),
                _display_( /*240.33*/ Messages(s"$key.submit-subheading")),
                format.raw /*240.68*/ ("""</h2>
    <p>"""),
                _display_( /*241.9*/ Messages(s"$key.submit-paragraph")),
                format.raw /*241.43*/ ("""</p>
""")
              ))
          }),
          format.raw /*242.2*/ ("""

"""),
          format.raw /*244.1*/ (
            """<p>
    <a href="javascript:window.print()" class="print-link" onclick="ga('send', 'event', 'print', 'click', 'Print')">
       """),
          _display_( /*246.9*/ Messages("sdil.common.print")),
          format.raw /*246.38*/ ("""
    """),
          format.raw /*247.5*/ ("""</a>
</p>""")
        )
      }
    }
  }

  def render(
    key: String,
    lineItems: List[scala.Tuple3[String, scala.Tuple2[Long, Long], Int]],
    costLower: BigDecimal,
    costHigher: BigDecimal,
    subtotal: BigDecimal,
    broughtForward: BigDecimal,
    total: BigDecimal,
    variation: Option[ReturnsVariation],
    subscription: RetrievedSubscription,
    originalReturn: Option[SdilReturn],
    m: Messages): play.twirl.api.HtmlFormat.Appendable =
    apply(
      key,
      lineItems,
      costLower,
      costHigher,
      subtotal,
      broughtForward,
      total,
      variation,
      subscription,
      originalReturn)(m)

  def f: (
    (
      String,
      List[scala.Tuple3[String, scala.Tuple2[Long, Long], Int]],
      BigDecimal,
      BigDecimal,
      BigDecimal,
      BigDecimal,
      BigDecimal,
      Option[ReturnsVariation],
      RetrievedSubscription,
      Option[SdilReturn]) => (Messages) => play.twirl.api.HtmlFormat.Appendable) =
    (key, lineItems, costLower, costHigher, subtotal, broughtForward, total, variation, subscription, originalReturn) =>
      (m) =>
        apply(
          key,
          lineItems,
          costLower,
          costHigher,
          subtotal,
          broughtForward,
          total,
          variation,
          subscription,
          originalReturn)(m)

  def ref: this.type = this

  /*
    -- GENERATED --
    DATE: Thu Feb 03 14:20:22 GMT 2022
    SOURCE: /Users/jakereid/Documents/Services/soft-drinks-industry-levy-frontend/app/views/uniform/fragments/returnsCYA.scala.html
    HASH: 2a20a9eca6a96f677cc96868d40b7465e80ae227
    MATRIX: 554->606|598->643|636->674|695->726|1245->779|1654->1094|1682->1095|1749->1136|1772->1150|1812->1152|1848->1161|1889->1175|1960->1225|1999->1247|2012->1252|2051->1253|2088->1263|2110->1276|2150->1278|2191->1291|2232->1305|2303->1355|2347->1381|2360->1386|2399->1387|2440->1400|2481->1414|2559->1471|2615->1496|2651->1502|2683->1507|2786->1583|2833->1609|2921->1670|2964->1692|3060->1761|3105->1785|3201->1854|3244->1876|3354->1959|3417->2006|3457->2008|3498->2021|3563->2059|3610->2085|3664->2112|3706->2133|3776->2176|3817->2196|3905->2257|3975->2305|4081->2384|4109->2391|4160->2415|4205->2439|4254->2460|4364->2543|4407->2565|4477->2608|4518->2628|4607->2690|4678->2739|4784->2818|4812->2825|4863->2849|4908->2873|4957->2894|5042->2948|5079->2958|5114->2984|5154->2986|5190->2995|5291->3069|5338->3095|5465->3195|5509->3217|5645->3326|5708->3367|5835->3467|5891->3501|5958->3537|5995->3547|6031->3574|6071->3576|6112->3590|6135->3604|6148->3608|6193->3615|6239->3633|6320->3687|6373->3719|6447->3766|6489->3787|6614->3885|6662->3912|6736->3959|6779->3981|6904->4079|6956->4110|7030->4157|7085->4191|7182->4261|7216->4286|7256->4288|7309->4313|7393->4370|7454->4409|7507->4444|7520->4449|7559->4450|7612->4475|7696->4532|7757->4571|7822->4605|7872->4626|7967->4693|8019->4722|8099->4770|8130->4782|8144->4787|8184->4788|8226->4801|8332->4879|8377->4902|8509->5006|8551->5025|8623->5065|8656->5070|8704->5091|8728->5105|8742->5109|8788->5116|8821->5122|8856->5147|8897->5149|8934->5158|9058->5254|9117->5291|9159->5304|9203->5330|9217->5335|9257->5336|9294->5345|9341->5361|9374->5363|9408->5370|9427->5379|9441->5383|9495->5398|9534->5409|9579->5444|9620->5446|9663->5460|9756->5525|9818->5565|9988->5707|10054->5751|10108->5776|10212->5852|10269->5887|10323->5912|10450->6011|10502->6046|10543->6048|10593->6069|10863->6311|10877->6315|10918->6334|11030->6418|11083->6454|11124->6456|11190->6493|11243->6518|11269->6522|11341->6562|11403->6595|11457->6621|11471->6625|11510->6642|11643->6747|11657->6751|11696->6768|11905->6949|11977->6998|12120->7109|12166->7126|12237->7165|12276->7176|12319->7209|12360->7211|12402->7224|12495->7289|12558->7330|12728->7472|12787->7509|12841->7534|12945->7610|13002->7645|13056->7670|13183->7769|13233->7802|13274->7804|13324->7825|13631->8104|13672->8135|13704->8138|13718->8142|13745->8158|13759->8163|13790->8165|13805->8169|13850->8189|13916->8226|14028->8310|14089->8354|14130->8356|14196->8393|14249->8418|14275->8422|14347->8462|14409->8495|14463->8521|14477->8525|14516->8542|14649->8647|14663->8651|14702->8668|14915->8853|14988->8903|15131->9014|15177->9031|15248->9070|15287->9081|15345->9129|15386->9131|15428->9144|15484->9172|15545->9211|15592->9226|15631->9237|15739->9334|15781->9336|15823->9349|15855->9353|15952->9428|15998->9442|16037->9453|16144->9549|16186->9551|16228->9564|16260->9568|16358->9644|16404->9658|16443->9669|16518->9734|16559->9736|16601->9749|16633->9753|16722->9820|16768->9834|16807->9845|16880->9908|16921->9910|16963->9923|16995->9927|17086->9996|17132->10010|17171->10021|17245->10085|17286->10087|17328->10100|17360->10104|17454->10176|17500->10190|17537->10196|17567->10199|17603->10225|17644->10227|17677->10232|17733->10260|17790->10295|17831->10309|17887->10343|17924->10349|17954->10351|18110->10480|18161->10509|18194->10514
    LINES: 20->17|21->18|22->19|23->20|28->22|33->23|34->24|36->26|36->26|36->26|37->27|37->27|37->27|38->28|38->28|38->28|39->29|39->29|39->29|40->30|40->30|40->30|41->31|41->31|41->31|42->32|42->32|42->32|43->33|44->34|45->35|47->37|47->37|48->38|48->38|49->39|49->39|50->40|50->40|56->46|56->46|56->46|57->47|58->48|58->48|59->49|59->49|60->50|60->50|61->51|61->51|63->53|63->53|64->54|64->54|65->55|69->59|69->59|70->60|70->60|71->61|71->61|73->63|73->63|74->64|74->64|75->65|78->68|79->69|79->69|79->69|80->70|81->71|81->71|82->72|82->72|85->75|85->75|86->76|86->76|88->78|89->79|89->79|89->79|90->80|90->80|90->80|90->80|92->82|93->83|93->83|94->84|94->84|97->87|97->87|98->88|98->88|101->91|101->91|102->92|102->92|105->95|105->95|105->95|106->96|106->96|106->96|107->97|107->97|107->97|108->98|108->98|108->98|109->99|110->100|110->100|110->100|112->102|114->104|114->104|114->104|115->105|116->106|116->106|117->107|117->107|119->109|120->110|124->114|124->114|124->114|124->114|125->115|125->115|125->115|126->116|129->119|129->119|130->120|132->122|132->122|132->122|133->123|134->124|135->125|137->127|137->127|137->127|137->127|139->129|139->129|139->129|141->131|142->132|142->132|147->137|147->137|148->138|150->140|150->140|151->141|156->146|156->146|156->146|157->147|161->151|161->151|161->151|163->153|163->153|163->153|164->154|164->154|164->154|165->155|166->156|166->156|166->156|166->156|169->159|169->159|169->159|172->162|172->162|176->166|177->167|179->169|181->171|181->171|181->171|182->172|183->173|183->173|188->178|188->178|189->179|191->181|191->181|192->182|197->187|197->187|197->187|198->188|203->193|203->193|203->193|203->193|203->193|203->193|203->193|203->193|203->193|204->194|206->196|206->196|206->196|207->197|207->197|207->197|208->198|209->199|209->199|209->199|209->199|212->202|212->202|212->202|215->205|215->205|219->209|220->210|222->212|224->214|224->214|224->214|225->215|225->215|225->215|226->216|228->218|228->218|228->218|229->219|229->219|229->219|230->220|232->222|232->222|232->222|233->223|233->223|233->223|234->224|236->226|236->226|236->226|237->227|237->227|237->227|238->228|240->230|240->230|240->230|241->231|241->231|241->231|242->232|244->234|244->234|244->234|245->235|245->235|245->235|246->236|247->237|249->239|249->239|249->239|250->240|250->240|250->240|251->241|251->241|252->242|254->244|256->246|256->246|257->247
    -- GENERATED --
 */

}
