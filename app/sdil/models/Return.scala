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

package sdil.models

import cats.Order
import java.time.LocalDate
case class SdilReturn(
  ownBrand     : (Long,Long),
  packLarge    : (Long,Long),
  packSmall    : List[SmallProducer],
  importLarge  : (Long,Long),
  importSmall  : (Long,Long),
  export       : (Long,Long),
  wastage      : (Long,Long)
)

case class ReturnPeriod(year: Int, quarter: Int) {
  require(quarter <= 3 && quarter >= 0)
  require(year >= 2018)
  def start: LocalDate = LocalDate.of(year, quarter * 3 + 1, if (count == 0) 5 else 1)
  def end: LocalDate = next.start.minusDays(1)
  def deadline: LocalDate = end.plusDays(30)
  def next: ReturnPeriod = ReturnPeriod(count + 1)
  def previous: ReturnPeriod = ReturnPeriod(count - 1)
  def count: Int = year * 4 + quarter - 2018 * 4 - 1
}

object ReturnPeriod {
  def apply(o: Int): ReturnPeriod = {
    val i = o + 1
    ReturnPeriod(2018 + i / 4, i % 4)
  }
  def apply(date: LocalDate): ReturnPeriod = ReturnPeriod(date.getYear, quarter(date))
  def quarter(date: LocalDate): Int = { date.getMonthValue - 1 } / 3
}
