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
import cats.implicits._

package object play {

  implicit class RichMessages(mo: Messages.type) {

    def get(key: String, args: Any*)(implicit provider: Messages): Option[String] = {
      if (provider.isDefinedAt(key))
        provider.messages(key, args:_*).some
      else
        none[String]
    }

    def many(
      key: String,
      args: Any*)(implicit provider: Messages): List[String] =
    {

      @annotation.tailrec
      def inner(cnt: Int = 2, list: List[String] = Nil): List[String] =
        get(s"$key.$cnt", args:_*) match {
          case Some(_) => inner(cnt+1, provider.messages(s"$key.$cnt", args:_*) :: list)
          case None       => list
        }

      List(key, s"$key.1").map(get(_, args)).flatten ++ inner().reverse
    }

  }

}
