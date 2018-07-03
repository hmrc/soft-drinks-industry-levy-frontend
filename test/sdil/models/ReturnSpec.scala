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

import org.scalatest.{ FlatSpec, Matchers }
import java.time.LocalDate
import org.scalacheck.Arbitrary._
import org.scalacheck.Prop._
import org.scalacheck.Gen
import org.scalatest.prop.PropertyChecks

class ReturnSpec extends FlatSpec with Matchers with PropertyChecks {

  val lowPosInts = Gen.choose(0, 1000)

  "A ReturnPeriod" should "be indexed correctly" in {
    forAll (lowPosInts) {
      i =>  ReturnPeriod(i).count should be (i)
    }
  }

  it should "contain its start date" in {
    forAll (lowPosInts) { i =>
      val period = ReturnPeriod(i)
      ReturnPeriod(period.start) should be (period)
    }
  }

  it should "contain its end date" in {
    forAll (lowPosInts) { i =>
      val period = ReturnPeriod(i)
      ReturnPeriod(period.end) should be (period)
    }
  } 

  it should "give the correct quarter for predefined dates" in {
    ReturnPeriod(LocalDate.of(2018, 4, 15)).quarter should be (1)
    ReturnPeriod(LocalDate.of(2018, 8, 15)).quarter should be (2)
    ReturnPeriod(LocalDate.of(2018, 12, 15)).quarter should be (3)
    ReturnPeriod(LocalDate.of(2019, 2, 15)).quarter should be (0)
  }


  it should "start on the 5th April 2018 if it is the first" in {
    ReturnPeriod(0).start should be (LocalDate.of(2018, 4, 5))
    ReturnPeriod(LocalDate.of(2018, 4, 8)) should be (ReturnPeriod(0))
  }

  it should "increment correctly" in {
    ReturnPeriod(0).next should be (ReturnPeriod(1))
    ReturnPeriod(0).end.plusDays(1) should be (ReturnPeriod(1).start)
  }
}
