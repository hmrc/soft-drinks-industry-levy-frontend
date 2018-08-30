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

import play.api.mvc.Call
import sdil.controllers.routes

sealed trait Page {
  def isVisible(implicit formData: RegistrationFormData): Boolean

  def isComplete(implicit formData: RegistrationFormData): Boolean

  def isIncomplete(implicit formData: RegistrationFormData): Boolean = !isComplete(formData)

  def show: Call
}

case object IdentifyPage extends Page {
  override def isVisible(implicit formData: RegistrationFormData): Boolean = true

  override def isComplete(implicit formData: RegistrationFormData): Boolean = true

  override def show: Call = routes.IdentifyController.show()
}

case object VerifyPage extends Page {
  override def isVisible(implicit formData: RegistrationFormData): Boolean = true

  override def isComplete(implicit formData: RegistrationFormData): Boolean = formData.verify.isDefined

  override def show: Call = routes.VerifyController.show()
}
