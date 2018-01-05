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

import org.mockito.ArgumentMatchers.{eq => matching, _}
import org.mockito.Mockito._
import sdil.models._
import sdil.utils.FakeApplicationSpec

import scala.concurrent.Future

trait ControllerSpec extends FakeApplicationSpec {

  def stubCacheEntry(value: Option[RegistrationFormData]) = {
    when(mockCache.get(matching("internal id"))(any(), any())).thenReturn(Future.successful(value))
  }

  def verifyDataCached(formData: RegistrationFormData) = {
    verify(mockCache, times(1)).cache(matching("internal id"), matching(formData))(any(), any())
  }

  def stubFormPage(rosmData: RosmRegistration = defaultRosmData,
                   utr: String = defaultFormData.utr,
                   verify: Option[DetailsCorrect] = defaultFormData.verify,
                   orgType: Option[String] = defaultFormData.orgType,
                   packaging: Option[Packaging] = defaultFormData.packaging,
                   packageOwn: Option[Litreage] = defaultFormData.packageOwn,
                   packageCopack: Option[Litreage] = defaultFormData.packageCopack,
                   packageCopackSmall: Option[Boolean] = defaultFormData.packageCopackSmall,
                   packageCopackSmallVol: Option[Litreage] = defaultFormData.packageCopackSmallVol,
                   copacked: Option[Boolean] = defaultFormData.copacked,
                   copackedVolume: Option[Litreage] = defaultFormData.copackedVolume,
                   imports: Option[Boolean] = defaultFormData.imports,
                   importVolume: Option[Litreage] = defaultFormData.importVolume,
                   startDate: Option[LocalDate] = defaultFormData.startDate,
                   productionSites: Option[Seq[Address]] = defaultFormData.productionSites,
                   secondaryWarehouses: Option[Seq[Address]] = defaultFormData.secondaryWarehouses,
                   contactDetails: Option[ContactDetails] = defaultFormData.contactDetails) = {

    stubCacheEntry(Some(RegistrationFormData(
      rosmData,
      utr,
      verify,
      orgType,
      packaging,
      packageOwn,
      packageCopack,
      packageCopackSmall,
      packageCopackSmallVol,
      copacked,
      copackedVolume,
      imports,
      importVolume,
      startDate,
      productionSites,
      secondaryWarehouses,
      contactDetails
    )))
  }

  def stubFilledInForm = {
    stubCacheEntry(
      Some(defaultFormData)
    )
  }

  when(mockSdilConnector.getRosmRegistration(any())(any())).thenReturn(Future.successful(Some(defaultRosmData)))

  lazy val defaultFormData: RegistrationFormData = {
    RegistrationFormData(
      rosmData = defaultRosmData,
      utr = "1234567890",
      verify = Some(DetailsCorrect.Yes),
      orgType = Some("partnership"),
      packaging = Some(Packaging(
        isLiable = true,
        ownBrands = true,
        customers = true
      )),
      packageOwn = Some(Litreage(
        atLowRate = 1,
        atHighRate = 2
      )),
      packageCopack = Some(Litreage(
        atLowRate = 3,
        atHighRate = 4
      )),
      packageCopackSmall = Some(true),
      packageCopackSmallVol = Some(Litreage(
        atLowRate = 5,
        atHighRate = 6
      )),
      copacked = Some(true),
      copackedVolume = Some(Litreage(
        atLowRate = 7,
        atHighRate = 8
      )),
      imports = Some(true),
      importVolume = Some(Litreage(
        atLowRate = 9,
        atHighRate = 10
      )),
      startDate = Some(LocalDate.of(2018, 4, 6)),
      productionSites = Some(Seq(Address("1 Production Site St", "Production Site Town", "", "", "AA11 1AA"))),
      secondaryWarehouses = Some(Seq(Address("1 Warehouse Site St", "Warehouse Site Town", "", "", "AA11 1AA"))),
      contactDetails = Some(ContactDetails(
        fullName = "A person",
        position = "A position",
        phoneNumber = "1234",
        email = "aa@bb.cc"
      ))
    )
  }

  lazy val defaultRosmData = RosmRegistration(
    "some-safe-id",
    OrganisationDetails(
      "an organisation",
      Some(RosmOrganisationType.CorporateBody)
    ),
    Address("1", "The Road", "", "", "AA11 1AA")
  )
}
