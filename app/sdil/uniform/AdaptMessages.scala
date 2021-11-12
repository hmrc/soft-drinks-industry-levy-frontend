/*
 * Copyright 2021 HM Revenue & Customs
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

import java.util.Locale

import ltbs.uniform._
import play.api.i18n.{Lang, Messages}
import play.api.mvc.RequestHeader
import play.twirl.api.Html

object AdaptMessages {

  /* Used inside twirl views to turn a UniformMessages object into a Play Messages one for compatibility with old helper templates */
  implicit def ufMessagesToPlayMessages(implicit ufMessages: UniformMessages[Html]): Messages =
    new Messages {
      def lang: Lang = new Lang(Locale.ENGLISH)
      def apply(key: String, args: Any*): String = ufMessages.apply(key, args: _*).toString
      def apply(keys: Seq[String], args: Any*): String = ufMessages.apply(keys.toList, args: _*).toString
      def translate(key: String, args: Seq[Any]): Option[String] = ufMessages.get(key, args: _*).map { _.toString }
      def isDefinedAt(key: String): Boolean = ufMessages.get(key).isDefined
      def asJava: play.i18n.Messages = ???
    }

}
