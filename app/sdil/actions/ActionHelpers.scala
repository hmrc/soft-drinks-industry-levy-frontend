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

package sdil.actions

import uk.gov.hmrc.auth.core.{EnrolmentIdentifier, Enrolments}

trait ActionHelpers {
  protected def getSdilEnrolment(enrolments: Enrolments): Option[EnrolmentIdentifier] = {
    val sdil = for {
      enrolment <- enrolments.enrolments if enrolment.key.equalsIgnoreCase("HMRC-OBTDS-ORG")
      sdil      <- enrolment.getIdentifier("EtmpRegistrationNumber") if sdil.value.slice(2, 4) == "SD"
    } yield {
      sdil
    }

    sdil.headOption
  }

  protected def getUtr(enrolments: Enrolments): Option[String] =
    enrolments
      .getEnrolment("IR-CT")
      .orElse(enrolments.getEnrolment("IR-SA"))
      .flatMap(_.getIdentifier("UTR").map(_.value))
}
