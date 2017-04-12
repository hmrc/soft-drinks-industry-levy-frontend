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

package uk.gov.hmrc.softdrinksindustrylevyfrontend.util

case class User(credId: String, utr: String, pass: String)

object Users {
  private val DEFAULT_DEV_USER = User("543212300016", "1097172564", "testing123")
  private val DEFAULT_STAGING_USER = User(RandomUtils.randString(12), "1097172564", "testing123")
  private val DEFAULT_QA_USER = User("SSTTPCREDID02", "2435657686", "")

  val DEFAULT_USER = {
    if (Env.isQA(Env.baseUrl)) DEFAULT_QA_USER
    else if (Env.isStaging(Env.baseUrl)) DEFAULT_STAGING_USER
    else DEFAULT_DEV_USER
  }
}


