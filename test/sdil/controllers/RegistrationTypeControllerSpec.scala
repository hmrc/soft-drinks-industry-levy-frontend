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

import com.softwaremill.macwire._
import org.scalatest.BeforeAndAfterEach
import play.api.test.FakeRequest
import play.api.test.Helpers._
import sdil.models.{Litreage, Producer}

class RegistrationTypeControllerSpec extends ControllerSpec with BeforeAndAfterEach {

  "The registration type controller" should {
    "redirect to the registration not required page if they only produce for themselves, and the volume is fewer than 1 million litres" in {
      stubFormPage(
        producer = Some(Producer(isProducer = true, isLarge = Some(false))),
        packageOwnVol = Some(Litreage(1, 2)),
        packagesForOthers = Some(false),
        volumeForCustomerBrands = None,
        usesCopacker = Some(false),
        imports = Some(false),
        importVolume = None
      )

      val res = testController.continue()(FakeRequest())

      status(res) mustBe SEE_OTHER
      redirectLocation(res).value mustBe routes.RegistrationTypeController.registrationNotRequired().url
    }

    "redirect to the start date page if the user packages, or has packaged on their behalf, more than 1 million litres" in {
      stubFormPage(
        producer = Some(Producer(isProducer = true, isLarge = Some(true))),
        packageOwnVol = Some(Litreage(1000000, 2)),
        packagesForOthers = Some(false),
        volumeForCustomerBrands = None,
        usesCopacker = Some(false),
        imports = Some(false),
        importVolume = None
      )

      val res = testController.continue()(FakeRequest())

      status(res) mustBe SEE_OTHER
      redirectLocation(res).value mustBe routes.StartDateController.show().url
    }

    "redirect to the start date page if the user produces fewer than 1 million litres, but copacks for others" in {
      stubFormPage(
        packageOwnVol = Some(Litreage(1, 2)),
        usesCopacker = Some(false),
        imports = Some(false),
        importVolume = None
      )

      val res = testController.continue()(FakeRequest())

      status(res) mustBe SEE_OTHER
      redirectLocation(res).value mustBe routes.StartDateController.show().url
    }

    "redirect to the start date page if the user produces fewer than 1 million litres, but imports liable drinks" in {
      stubFormPage(
        packageOwnVol = Some(Litreage(1, 2)),
        usesCopacker = Some(false),
        imports = Some(true),
        importVolume = Some(Litreage(1, 2))
      )

      val res = testController.continue()(FakeRequest())

      status(res) mustBe SEE_OTHER
      redirectLocation(res).value mustBe routes.StartDateController.show().url
    }

    "redirect to the start date page if the user produces fewer than 1 million litres, but uses a copacker" in {
      stubFormPage(
        packageOwnVol = Some(Litreage(1, 2)),
        usesCopacker = Some(true),
        imports = Some(false),
        importVolume = None
      )

      val res = testController.continue()(FakeRequest())

      status(res) mustBe SEE_OTHER
      redirectLocation(res).value mustBe routes.StartDateController.show().url
    }

    "redirect to the start date page if the user only imports" in {
      stubFormPage(
        isPackagingForSelf = Some(false),
        packageOwnVol = None,
        usesCopacker = Some(false),
        imports = Some(true),
        importVolume = Some(Litreage(1, 2))
      )

      val res = testController.continue()(FakeRequest())

      status(res) mustBe SEE_OTHER
      redirectLocation(res).value mustBe routes.StartDateController.show().url
    }

    "redirect to the start date page if the user only copacks" in {
      stubFormPage(
        isPackagingForSelf = Some(false),
        packageOwnVol = None,
        volumeForCustomerBrands = Some(Litreage(1, 2)),
        usesCopacker = Some(false),
        imports = Some(false),
        importVolume = None
      )

      val res = testController.continue()(FakeRequest())

      status(res) mustBe SEE_OTHER
      redirectLocation(res).value mustBe routes.StartDateController.show().url
    }

    "redirect to the do not register page if the user does not package, import, copack, or have drinks packaged for them" in {
      stubFormPage(
        producer = Some(Producer(isProducer = false, isLarge = None)),
        packageOwnVol = None,
        packagesForOthers = Some(false),
        volumeForCustomerBrands = None,
        usesCopacker = Some(false),
        imports = Some(false),
        importVolume = None
      )

      val res = testController.continue()(FakeRequest())
      status(res) mustBe SEE_OTHER
      redirectLocation(res).value mustBe routes.RegistrationTypeController.registrationNotRequired().url
    }

    "clear any saved production sites if the user does not package over 1 million litres, and is not a copacker" in {
      stubFormPage(
        utr = "112233445",
        producer = Some(Producer(isProducer = true, isLarge = Some(false))),
        isPackagingForSelf = Some(true),
        packageOwnVol = Some(Litreage(12, 21)),
        packagesForOthers = Some(false),
        volumeForCustomerBrands = None,
        imports = Some(true)
      )

      val res = testController.continue()(FakeRequest())
      status(res) mustBe SEE_OTHER

      verifyDataCached(
        defaultFormData.copy(
          utr = "112233445",
          producer = Some(Producer(isProducer = true, isLarge = Some(false))),
          isPackagingForSelf = Some(true),
          volumeForOwnBrand = Some(Litreage(12, 21)),
          packagesForOthers = Some(false),
          volumeForCustomerBrands = None,
          isImporter = Some(true),
          productionSites = Some(Nil)
        )
      )
    }

    "clear any saved production sites and warehouse sites if the user is registering voluntarily" in {
      stubFormPage(
        utr = "112233446",
        producer = Some(Producer(isProducer = true, isLarge = Some(false))),
        isPackagingForSelf = Some(true),
        packageOwnVol = Some(Litreage(12, 21)),
        packagesForOthers = Some(false),
        volumeForCustomerBrands = None,
        imports = Some(false),
        importVolume = None
      )

      val res = testController.continue()(FakeRequest())
      status(res) mustBe SEE_OTHER

      verifyDataCached(
        defaultFormData.copy(
          utr = "112233446",
          producer = Some(Producer(isProducer = true, isLarge = Some(false))),
          isPackagingForSelf = Some(true),
          volumeForOwnBrand = Some(Litreage(12, 21)),
          packagesForOthers = Some(false),
          volumeForCustomerBrands = None,
          isImporter = Some(false),
          importVolume = None,
          productionSites = Some(Nil),
          secondaryWarehouses = Some(Nil)
        )
      )
    }
  }

  lazy val testController = wire[RegistrationTypeController]

  override protected def beforeEach(): Unit = stubFilledInForm
}
