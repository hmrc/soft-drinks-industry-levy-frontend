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

package sdil.forms

import play.api.data.validation.Constraint
import play.api.data.{FormError, Mapping}

/**
  * a mapping with extra constraints that can't be represented correctly using Form.verifying
  */
trait MappingWithExtraConstraint[T] extends Mapping[T] {
  val underlying: Mapping[T]

  final override def unbind(value: T): Map[String, String] = underlying.unbind(value)

  final override def unbindAndValidate(value: T): (Map[String, String], Seq[FormError]) =
    underlying.unbindAndValidate(value)

  //not required
  final override val key: String = ""
  final override val mappings: Seq[Mapping[_]] = Nil
  final override val constraints: Seq[Constraint[T]] = Nil
  final override def withPrefix(prefix: String): Mapping[T] = this
  final override def verifying(constraints: Constraint[T]*): Mapping[T] = this
}
