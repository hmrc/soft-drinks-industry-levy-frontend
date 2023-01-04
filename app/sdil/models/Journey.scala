/*
 * Copyright 2023 HM Revenue & Customs
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

import sdil.actions.RegistrationFormRequest

object Journey {
  private val pages: List[Page] = List(
    IdentifyPage,
    VerifyPage
  )

  private val reversed: List[Page] = pages.reverse

  def expectedPage(page: Page)(implicit request: RegistrationFormRequest[_]): Page = {
    implicit val formData: RegistrationFormData = request.formData

    if (page.isVisible) {
      pages.takeWhile(_ != page).find(p => p.isVisible && p.isIncomplete).getOrElse(page)
    } else {
      pages.find(p => p.isVisible && p.isIncomplete).getOrElse(pages.last)
    }
  }

  def previousPage(page: Page)(implicit request: RegistrationFormRequest[_]): Page = {
    implicit val formData: RegistrationFormData = request.formData

    if (page == pages.head) {
      pages.head
    } else {
      reversed.dropWhile(_ != page).tail.find(_.isVisible).getOrElse(pages.head)
    }
  }

  def nextPage(page: Page, formData: RegistrationFormData): Page = {
    implicit val fd: RegistrationFormData = formData
    pages.dropWhile(_ != page).tail.find(p => p.isVisible).getOrElse(pages.last)
  }
}
