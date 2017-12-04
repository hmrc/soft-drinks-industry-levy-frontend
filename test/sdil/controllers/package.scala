/*
 * Copyright 2017 HM Revenue & Customs
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

import org.scalatest.mockito.MockitoSugar
import sdil.models.Packaging
import uk.gov.hmrc.auth.core._

import scala.concurrent.Future

package object controllerhelpers extends MockitoSugar {

  val userWithUtr: Future[Option[String]] = Future successful Some("UTR")
  val userNoUtr: Future[Option[String]] = Future successful None

  val notLoggedIn: Future[Option[String]] = Future failed new InvalidBearerToken

  val validPackageFormCustomersOnly = Seq(
    "isLiable" -> "true",
    "customers" -> "true",
    "ownBrands" -> "false")

  val invalidStartDateForm = Seq(
    "startDateDay" -> "29",
    "startDateMonth" -> "02",
    "startDateYear" -> "2017"
  )
  val invalidStartDateDayForm = Seq(
    "startDateDay" -> "35",
    "startDateMonth" -> "08",
    "startDateYear" -> "2017"
  )
  val invalidStartDateMonthForm = Seq(
    "startDateDay" -> "20",
    "startDateMonth" -> "30",
    "startDateYear" -> "2017"
  )
  val invalidStartDateYearForm = Seq(
    "startDateDay" -> "20",
    "startDateMonth" -> "02",
    "startDateYear" -> "20189"
  )
  val packagingIsLiable = Packaging(true, true, false)
  val packagingIsntLiable = Packaging(false, false, false)
}