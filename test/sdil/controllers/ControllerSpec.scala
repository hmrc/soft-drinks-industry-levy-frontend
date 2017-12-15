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

import java.time.LocalDate

import org.mockito.ArgumentMatchers.{eq => matching, _}
import org.mockito.Mockito._
import org.mockito.stubbing.OngoingStubbing
import sdil.models._
import sdil.utils.FakeApplicationSpec
import uk.gov.hmrc.auth.core.InvalidBearerToken
import uk.gov.hmrc.auth.core.retrieve.Retrieval

import scala.concurrent.{ExecutionContext, Future}

trait ControllerSpec extends FakeApplicationSpec {

  def stubCacheEntry[T](key: String, value: Option[T]) = {
    when(mockCache.fetchAndGetEntry[T](matching(key))(any(), any(), any())).thenReturn(Future.successful(value))
  }

  def verifyDataCached(formData: RegistrationFormData) = {
    verify(mockCache, times(1)).cache(matching("formData"), matching(formData))(any(), any(), any())
  }

  def stubFormPage(identify: Identification = defaultFormData.identify,
                   verify: Option[DetailsCorrect] = defaultFormData.verify,
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

    stubCacheEntry[RegistrationFormData]("formData", Some(RegistrationFormData(
      identify,
      verify,
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

  def sdilAuthMock(returnValue: Future[Option[String]]): OngoingStubbing[Future[Option[String]]] =
    when(mockAuthConnector.authorise(any(), any[Retrieval[Option[String]]]())(any(), any[ExecutionContext]))
      .thenReturn(returnValue)

  val userWithUtr: Future[Option[String]] = Future successful Some("UTR")
  val userNoUtr: Future[Option[String]] = Future successful None

  val notLoggedIn: Future[Option[String]] = Future failed new InvalidBearerToken

  def stubFilledInForm = {
    stubCacheEntry(
      "formData",
      Some(defaultFormData)
    )
  }

  lazy val defaultFormData: RegistrationFormData = {
    RegistrationFormData(
      identify = Identification(
        utr = "1234567890",
        postcode = "AA11 1AA"
      ),
      verify = Some(DetailsCorrect.Yes),
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
}
