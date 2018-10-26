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

package sdil

import cats.Order
import cats.implicits._

package object models {

  implicit val returnPeriodOrder: Order[ReturnPeriod] = new Order[ReturnPeriod] {
    def compare(x: ReturnPeriod, y: ReturnPeriod): Int = x.count compare y.count
  }

  implicit class SmallProducerDetails(smallProducers: List[SmallProducer]) {
    def total: (Long, Long) = smallProducers.map(x => x.litreage).combineAll
  }

  def listItemsWithTotal(items: List[FinancialLineItem]): List[(FinancialLineItem, BigDecimal)] = {
    items.foldLeft(List.empty[(FinancialLineItem, BigDecimal)]) {
      (acc, n) => (n, acc.headOption.fold(n.amount)(_._2 + n.amount)) :: acc
    }
  }

  def extractTotal(l: List[(FinancialLineItem, BigDecimal)]): BigDecimal = {
    l.headOption.fold(BigDecimal(0))(_._2)
  }
}
