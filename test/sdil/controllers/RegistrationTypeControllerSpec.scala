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

import org.scalatest.BeforeAndAfterEach
import play.api.test.FakeRequest
import play.api.test.Helpers._
import sdil.models.{Litreage, Packaging}

class RegistrationTypeControllerSpec extends ControllerSpec with BeforeAndAfterEach {

  "The registration type controller" should {
    "redirect to the registration not required page if they only produce for themselves, and the volume is fewer than 1 million litres" in {
      stubFormPage(
        packaging = Some(Packaging(true, true, false)),
        packageOwn = Some(Litreage(1, 2)),
        packageCopack = None,
        packageCopackSmall = Some(false),
        packageCopackSmallVol = None,
        copacked = Some(false),
        copackedVolume = None,
        imports = Some(false),
        importVolume = None
      )

      val res = testController.continue()(FakeRequest())

      status(res) mustBe SEE_OTHER
      redirectLocation(res).value mustBe routes.RegistrationTypeController.registrationNotRequired().url
    }

    "redirect to the start date page if the user packages, or has packaged on their behalf, more than 1 million litres" in {
      stubFormPage(
        packaging = Some(Packaging(true, true, false)),
        packageOwn = Some(Litreage(1000000, 2)),
        packageCopack = None,
        packageCopackSmall = Some(false),
        packageCopackSmallVol = None,
        copacked = Some(false),
        copackedVolume = None,
        imports = Some(false),
        importVolume = None
      )

      val res = testController.continue()(FakeRequest())

      status(res) mustBe SEE_OTHER
      redirectLocation(res).value mustBe routes.StartDateController.displayStartDate().url
    }

    "redirect to the start date page if the user produces fewer 1 million litres, but copacks for others" in {
      stubFormPage(
        packaging = Some(Packaging(true, true, true)),
        packageOwn = Some(Litreage(1, 2)),
        packageCopackSmall = Some(false),
        packageCopackSmallVol = None,
        copacked = Some(false),
        copackedVolume = None,
        imports = Some(false),
        importVolume = None
      )

      val res = testController.continue()(FakeRequest())

      status(res) mustBe SEE_OTHER
      redirectLocation(res).value mustBe routes.SmallProducerConfirmController.displaySmallProducerConfirm().url
    }

    "redirect to the start date page if the user produces fewer 1 million litres, but imports liable drinks" in {
      stubFormPage(
        packaging = Some(Packaging(true, true, false)),
        packageOwn = Some(Litreage(1, 2)),
        packageCopackSmall = Some(false),
        packageCopackSmallVol = None,
        copacked = Some(false),
        copackedVolume = None,
        imports = Some(true),
        importVolume = Some(Litreage(1, 2))
      )

      val res = testController.continue()(FakeRequest())

      status(res) mustBe SEE_OTHER
      redirectLocation(res).value mustBe routes.SmallProducerConfirmController.displaySmallProducerConfirm().url
    }

    "redirect to the confirm exemption page if the registration is voluntary only" in {
      stubFormPage(
        packaging = Some(Packaging(true, true, false)),
        packageOwn = Some(Litreage(10000, 2)),
        packageCopackSmall = Some(false),
        packageCopackSmallVol = None,
        copacked = Some(true),
        copackedVolume = Some(Litreage(10000, 2)),
        imports = Some(false),
        importVolume = None
      )

      val res = testController.continue()(FakeRequest())

      status(res) mustBe SEE_OTHER
      redirectLocation(res).value mustBe routes.SmallProducerConfirmController.displaySmallProducerConfirm().url
    }

    "redirect to the confirm exemption and mandatory obligations page if the registration is voluntary/mandatory" in {
      stubFormPage(
        packaging = Some(Packaging(true, true, false)),
        packageOwn = Some(Litreage(10000, 2)),
        packageCopackSmall = Some(false),
        packageCopackSmallVol = None,
        copacked = Some(true),
        copackedVolume = Some(Litreage(10000, 2)),
        imports = Some(false),
        importVolume = None
      )

      val res = testController.continue()(FakeRequest())

      status(res) mustBe SEE_OTHER
      redirectLocation(res).value mustBe routes.SmallProducerConfirmController.displaySmallProducerConfirm().url

    }
  }

  lazy val testController = wire[RegistrationTypeController]

  override protected def beforeEach(): Unit = stubFilledInForm
}
