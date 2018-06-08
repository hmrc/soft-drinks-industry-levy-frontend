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

package ltbs

import _root_.play.api.i18n._
import _root_.play.api.data.Forms._
import _root_.play.api.data._
import _root_.play.api.data.format.Formats._

package object play {

  implicit class RichMessages(m: Messages) {
    def first(
      key: String,
      sndKey: String,
      fallbackKeys: String*
    )(args: Any*)(implicit lang: Lang): String = {
      val definedKey = (key :: sndKey :: fallbackKeys.toList)
        .find(m.isDefinedAt)
        .getOrElse(key)

      m(definedKey, args :_*)
    }
  }

  // implicit class RichForm(f: Form[_]) {
  //   def subForm(key: String): Form[Any] = {
  //     val mappings = f.mapping.mappings.toList.collect {
  //       case FieldMapping(key, constraints) => FieldMapping(key.replace("key", ""), constraints)
  //     }

  //     Form(mappings)
  //   }
  // }
}
