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

package sdil.controllers

import ltbs.uniform.UniformMessages
import play.twirl.api.Html
import sdil.models.ReturnsVariation

trait CheckYourAnswers[A] {
  def cyaRows(value: A, messages: UniformMessages[Html]): List[(String, Html, String)]
}

object CheckYourAnswers {
  implicit val returnsCya: CheckYourAnswers[ReturnsVariation] = new CheckYourAnswers[ReturnsVariation] {
    def cyaRows(value: ReturnsVariation, messages: UniformMessages[Html]): List[(String, Html, String)] = Nil
  }
}
