/*
 * Copyright 2020 HM Revenue & Customs
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
import play.api.libs.json._
import sdil.models._
import sdil.models.backend._
import sdil.models.retrieved.RetrievedSubscription
import sdil.utils.FakeApplicationSpec
import uk.gov.hmrc.http.cache.client.CacheMap

import scala.concurrent.Future

trait ControllerSpec extends FakeApplicationSpec {

  def stubCacheEntry(value: Option[RegistrationFormData]): OngoingStubbing[Future[Option[RegistrationFormData]]] =
    when(mockCache.get(matching("internal id"))(any()))
      .thenReturn(Future.successful(value))

  def verifyDataCached(formData: RegistrationFormData): Future[CacheMap] =
    verify(mockCache, times(1))
      .cache(matching("internal id"), matching(formData))(any())

  def returnsDataCheck(returnPeriods: List[ReturnPeriod]): OngoingStubbing[Future[List[ReturnPeriod]]] =
    when(mockSdilConnector.returns_variable(matching("utrNumber1234"))(any())).thenReturn {
      Future.successful(returnPeriods)
    }

  def submitRegistration(): OngoingStubbing[Future[Unit]] = {
    val mySub = defaultSubscription.copy(orgType = "7")
    when(mockSdilConnector.submit(any(), any())(any())) thenReturn Future.successful(())
  }

//  def desifySub(subscription: Subscription)= {
//    when(mockSubscription.desify(matching(defaultSubscription))) thenReturn subscription
//  }

  def returnsPendingCheck(returnPeriods: List[ReturnPeriod]): OngoingStubbing[Future[List[ReturnPeriod]]] =
    when(mockSdilConnector.returns_pending(matching("utrNumber1234"))(any())) thenReturn Future.successful(
      returnPeriods)

  def getOneReturn(sdilReturn: SdilReturn): OngoingStubbing[Future[Option[SdilReturn]]] =
    when(mockSdilConnector.returns_get(matching("0000000022"), matching(ReturnPeriod(2018, 1)))(any())) thenReturn Future
      .successful(Some(sdilReturn))

  def getSubscription(
    retrievedSubscription: RetrievedSubscription): OngoingStubbing[Future[Option[RetrievedSubscription]]] =
    when(mockSdilConnector.retrieveSubscription(matching("XZSDIL000100107"), any())(any()))
      .thenReturn(Future.successful(Some(retrievedSubscription)))

  def checkSmallProdStatus(isSmall: Boolean): OngoingStubbing[Future[Boolean]] =
    when(
      mockSdilWMController.isSmallProducer(
        matching("XPSDIL000000205"),
        matching(mockSdilConnector),
        matching(ReturnPeriod(2018, 3))
      )(any())
    ) thenReturn Future.successful(isSmall)

  def fetchAndGet(smallProd: JsValue): OngoingStubbing[Future[Option[JsValue]]] = {
    when(mockSdilConnector.shortLiveCache) thenReturn cacheMock
    when(
      cacheMock.fetchAndGetEntry[JsValue](
        matching("XCSDIL000000002"),
        matching("small-producer-details_data")
      )(any(), any(), any())).thenReturn {
      Future.successful(Some(smallProd))
    }
  }

  def balanceHistory(financialData: List[FinancialLineItem]): OngoingStubbing[Future[List[FinancialLineItem]]] =
    when(mockSdilConnector.balanceHistory(any(), any())(any())).thenReturn {
      Future.successful(financialData)
    }

  def balance(balance: BigDecimal): OngoingStubbing[Future[BigDecimal]] =
    when(mockSdilConnector.balance(any(), any())(any())).thenReturn {
      Future.successful(balance)
    }

  def stubFormPage(
    rosmData: RosmRegistration = defaultRosmData,
    utr: String = defaultFormData.utr,
    verify: Option[DetailsCorrect] = defaultFormData.verify,
    orgType: Option[String] = defaultFormData.organisationType,
    producer: Option[Producer] = defaultFormData.producer,
    isPackagingForSelf: Option[Boolean] = defaultFormData.isPackagingForSelf,
    packageOwnVol: Option[Litreage] = defaultFormData.volumeForOwnBrand,
    packagesForOthers: Option[Boolean] = defaultFormData.packagesForOthers,
    volumeForCustomerBrands: Option[Litreage] = defaultFormData.volumeForCustomerBrands,
    usesCopacker: Option[Boolean] = defaultFormData.usesCopacker,
    imports: Option[Boolean] = defaultFormData.isImporter,
    importVolume: Option[Litreage] = defaultFormData.importVolume,
    startDate: Option[LocalDate] = defaultFormData.startDate,
    productionSites: Option[Seq[Site]] = defaultFormData.productionSites,
    secondaryWarehouses: Option[Seq[Site]] = defaultFormData.secondaryWarehouses,
    contactDetails: Option[ContactDetails] = defaultFormData.contactDetails
  ): OngoingStubbing[Future[Option[RegistrationFormData]]] =
    stubCacheEntry(
      Some(
        RegistrationFormData(
          rosmData,
          utr,
          verify,
          orgType,
          producer,
          isPackagingForSelf,
          packageOwnVol,
          packagesForOthers,
          volumeForCustomerBrands,
          usesCopacker,
          imports,
          importVolume,
          startDate,
          productionSites,
          secondaryWarehouses,
          contactDetails
        )))

  def stubFilledInForm: OngoingStubbing[Future[Option[RegistrationFormData]]] =
    stubCacheEntry(
      Some(defaultFormData)
    )

  when(mockSdilConnector.getRosmRegistration(any())(any()))
    .thenReturn(Future.successful(Some(defaultRosmData)))

  lazy val defaultFormData: RegistrationFormData = {
    RegistrationFormData(
      rosmData = defaultRosmData,
      utr = "1234567890",
      verify = Some(DetailsCorrect.Yes),
      organisationType = Some("partnership"),
      producer = Some(Producer(isProducer = true, isLarge = Some(false))),
      isPackagingForSelf = Some(true),
      volumeForOwnBrand = Some(
        Litreage(
          atLowRate = 1,
          atHighRate = 2
        )),
      packagesForOthers = Some(true),
      volumeForCustomerBrands = Some(
        Litreage(
          atLowRate = 3,
          atHighRate = 4
        )),
      usesCopacker = Some(true),
      isImporter = Some(true),
      importVolume = Some(
        Litreage(
          atLowRate = 9,
          atHighRate = 10
        )),
      startDate = Some(LocalDate.of(2018, 4, 6)),
      productionSites = Some(
        Seq(
          Site.fromAddress(Address("1 Production Site St", "Production Site Town", "", "", "AA11 1AA"))
        )),
      secondaryWarehouses = Some(
        Seq(
          Site.fromAddress(Address("1 Warehouse Site St", "Warehouse Site Town", "", "", "AA11 1AA"))
        )),
      contactDetails = Some(
        ContactDetails(
          fullName = "A person",
          position = "A position",
          phoneNumber = "1234",
          email = "aa@bb.cc"
        ))
    )
  }

  lazy val defaultRosmData: RosmRegistration = RosmRegistration(
    "some-safe-id",
    Some(
      OrganisationDetails(
        "an organisation"
      )),
    None,
    Address("1", "The Road", "", "", "AA11 1AA")
  )
}
