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

package sdil

import ltbs.uniform.ErrorTree
import ltbs.uniform.interpreters.logictable.{Logic, SampleData}

package object journeys {

//  val testService: DSTService[Logic] = new TestDstService {}.get

  def instances[A](value: A*): SampleData[A] = new SampleData[A] {
    def apply(key: String): List[A] = value.toList
  }

  def instancesF[A](value: String => List[A]): SampleData[A] = new SampleData[A] {
    def apply(key: String): List[A] = value(key)
  }

  implicit class RichJourney[A](in: List[(List[_], Either[ErrorTree, A])]) {
    def asOutcome(debug: Boolean = false): A = {
      if (debug) {
        in.foreach {
          case (messages, outcome) =>
            println(messages.mkString("\n"))
            println(s"   => $outcome")
        }
      }

      in.head._2 match {
        case Right(a)        => a
        case Left(errorTree) => errorTree.toThrowable
      }
    }
  }

  implicit class RichErrorTree(in: ErrorTree) {
    def humanReadable: String =
      in.map {
          case (a, b) =>
            a.toList.flatten.mkString("::") + " -> " + b.toList.map(_.msg).mkString("::")
        }
        .mkString(";")

    def toThrowable =
      throw new IllegalStateException(in.humanReadable)
  }

}
