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

package sdil.controllers

import java.time.LocalDate

import sdil.models.backend.{Contact, UkAddress}
import sdil.models.retrieved.{RetrievedActivity, RetrievedSubscription}

package object variation {

  lazy val subscription: RetrievedSubscription = RetrievedSubscription(
    utr = "9876543210",
    orgName = "Forbidden Left Parenthesis & Sons",
    address = UkAddress(Seq("Rosm House", "Des Street", "Etmp Lane"), "SW1A 1AA"),
    activity = RetrievedActivity(
      smallProducer = true,
      largeProducer = true,
      contractPacker = false,
      importer = false,
      voluntaryRegistration = true
    ),
    liabilityDate = LocalDate.now,
    productionSites = Nil,
    warehouseSites = Nil,
    contact = Contact(Some("body"), Some("thing"), "-7", "aa@bb.cc")
  )
}
