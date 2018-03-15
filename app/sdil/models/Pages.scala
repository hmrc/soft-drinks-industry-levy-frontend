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

package sdil.models

import java.time.LocalDate
import play.api.mvc.Call
import sdil.controllers.routes
import sdil.config.AppConfig

sealed trait Page {

  def isComplete(formData: RegistrationFormData): Boolean

  def expectedPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = this

  def show: Call
}

sealed trait PageWithPreviousPage extends Page {
  def previousPage(formData: RegistrationFormData)(implicit config: AppConfig): Page

  override def  expectedPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = {
    previousPage(formData) match {
      case page if page.isComplete(formData) => this
      case page => page.expectedPage(formData)
    }
  }
}

sealed trait PageWithNextPage extends Page {
  def nextPage(formData: RegistrationFormData)(implicit config: AppConfig): Page
}

sealed trait MidJourneyPage extends PageWithPreviousPage with PageWithNextPage

case object IdentifyPage extends PageWithNextPage {
  override def nextPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = VerifyPage

  override def isComplete(formData: RegistrationFormData): Boolean = true

  override def show: Call = routes.IdentifyController.show()
}

case object VerifyPage extends MidJourneyPage {
  override def nextPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = formData.verify match {
    case Some(DetailsCorrect.No) => IdentifyPage
    case Some(_) => OrgTypePage
    case None => VerifyPage
  }

  override def previousPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = IdentifyPage

  override def isComplete(formData: RegistrationFormData): Boolean = formData.verify.isDefined

  override def show: Call = routes.VerifyController.show()
}

case object OrgTypePage extends MidJourneyPage {
  override def nextPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = formData.organisationType match {
    case Some(_) => PackagePage
    case None => OrgTypePage
  }

  override def previousPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = VerifyPage

  override def isComplete(formData: RegistrationFormData): Boolean = formData.organisationType.isDefined

  override def show: Call = routes.OrganisationTypeController.show
}

case object ProducerPage extends MidJourneyPage {
  // TODO fix where nextPage goes
  override def nextPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = PackageCopackSmallPage

  override def previousPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = OrgTypePage

  override def isComplete(formData: RegistrationFormData): Boolean = formData.producer.isDefined

  override def show: Call = routes.ProducerController.show()
}

case object PackagePage extends MidJourneyPage {
  override def nextPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = formData.packaging match {
    case Some(p) if p.isPackager && p.packagesOwnBrand => PackageOwnPage
    case Some(p) if p.isPackager && p.packagesCustomerBrands => PackageCopackPage
    case Some(p) => CopackedPage
    case None => PackagePage
  }

  override def previousPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = OrgTypePage

  override def isComplete(formData: RegistrationFormData): Boolean = formData.packaging.isDefined

  override def show: Call = routes.PackageController.show()
}

case object PackageOwnPage extends MidJourneyPage {
  override def nextPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = formData.packaging match {
    case Some(p) if p.isPackager && p.packagesCustomerBrands => PackageCopackPage
    case Some(p) => CopackedPage
    case None => PackagePage
  }

  override def previousPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = PackagePage

  override def isComplete(formData: RegistrationFormData): Boolean = formData.volumeForOwnBrand.isDefined

  override def show: Call = routes.LitreageController.show("packageOwn")
}

case object PackageCopackPage extends MidJourneyPage {
  override def nextPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = PackageCopackSmallPage

  override def previousPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = formData.packaging match {
    case Some(p) if p.isPackager && p.packagesOwnBrand => PackageOwnPage
    case _ => PackagePage
  }

  override def isComplete(formData: RegistrationFormData): Boolean = formData.volumeForCustomerBrands.isDefined

  override def show: Call = routes.LitreageController.show("packageCopack")
}

case object PackageCopackSmallPage extends MidJourneyPage {
  override def nextPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = formData.packagesForSmallProducers match {
    case Some(true) => PackageCopackSmallVolPage
    case Some(false) => CopackedPage
    case None => PackageCopackPage
  }

  override def previousPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = formData.packaging match {
    case Some(p) if p.isPackager && p.packagesCustomerBrands => PackageCopackPage
    case Some(p) if p.isPackager && p.packagesOwnBrand => PackageOwnPage
    case _ => PackagePage
  }

  override def isComplete(formData: RegistrationFormData): Boolean = formData.packagesForSmallProducers.isDefined

  override def show: Call = routes.RadioFormController.show("package-copack-small")
}

case object PackageCopackSmallVolPage extends MidJourneyPage {
  override def nextPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = CopackedPage

  override def previousPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = PackageCopackSmallPage

  override def isComplete(formData: RegistrationFormData): Boolean = formData.volumeForSmallProducers.isDefined

  override def show: Call = routes.VolumeForSmallProducersController.show
}

case object CopackedPage extends MidJourneyPage {
  override def nextPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = formData.usesCopacker match {
    case Some(true) => CopackedVolumePage
    case Some(false) => ImportPage
    case None => CopackedPage
  }

  override def previousPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = formData.packaging match {
    case Some(p) if p.packagesCustomerBrands && formData.packagesForSmallProducers.contains(true) => PackageCopackSmallVolPage
    case Some(p) if p.packagesCustomerBrands && !formData.packagesForSmallProducers.contains(true) => PackageCopackSmallPage
    case Some(p) if p.packagesOwnBrand && !p.packagesCustomerBrands => PackageOwnPage
    case Some(p) if p.packagesCustomerBrands => PackageCopackPage
    case _ => PackagePage
  }

  override def isComplete(formData: RegistrationFormData): Boolean = formData.usesCopacker.isDefined

  override def show: Call = routes.RadioFormController.show("copacked")
}

case object CopackedVolumePage extends MidJourneyPage {
  override def nextPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = ImportPage

  override def previousPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = CopackedPage

  override def isComplete(formData: RegistrationFormData): Boolean = formData.volumeByCopackers.isDefined

  override def show: Call = routes.LitreageController.show("copackedVolume")
}

case object ImportPage extends MidJourneyPage {
  override def nextPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = formData.isImporter match {
    case Some(true) => ImportVolumePage
    case Some(false) => RegistrationTypePage
    case None => ImportPage
  }

  override def previousPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = formData.usesCopacker match {
    case Some(true) => CopackedVolumePage
    case _ => CopackedPage
  }

  override def isComplete(formData: RegistrationFormData): Boolean = formData.isImporter.isDefined

  override def show: Call = routes.RadioFormController.show("import")
}

case object ImportVolumePage extends MidJourneyPage {
  override def nextPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = RegistrationTypePage

  override def previousPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = ImportPage

  override def isComplete(formData: RegistrationFormData): Boolean = formData.importVolume.isDefined

  override def show: Call = routes.LitreageController.show("importVolume")
}

case object RegistrationTypePage extends MidJourneyPage {
  override def previousPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = formData.isImporter match {
    case Some(true) => ImportVolumePage
    case _ => ImportPage
  }

  override def nextPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = StartDatePage

  override def isComplete(formData: RegistrationFormData): Boolean = true

  override def show: Call = routes.RegistrationTypeController.continue()
}

case object StartDatePage extends MidJourneyPage {

  override def nextPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = (formData, formData.packaging) match {
    case (f, _) if f.isVoluntary => ContactDetailsPage
    case (_, Some(p)) if p.isPackager => ProductionSitesPage
    case (_, Some(p)) => WarehouseSitesPage
    case _ => PackagePage
  }

  override def previousPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = formData match {
    case form if formData.isSmall => SmallProducerConfirmPage
    case form if form.isImporter.contains(true) => ImportVolumePage
    case _ => ImportPage
  }

  override def isComplete(formData: RegistrationFormData): Boolean = formData.startDate.isDefined

  override def show: Call = routes.StartDateController.show()
}

case object ProductionSitesPage extends MidJourneyPage {
  override def nextPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = WarehouseSitesPage

  override def previousPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = {
    val showStartDate = LocalDate.now isAfter config.taxStartDate
    (formData.isImporter, formData.isSmall, showStartDate) match {
      case (Some(true), false, false)  => ImportVolumePage
      case (_, true, false) => SmallProducerConfirmPage
      case (_, false, false) => ImportPage
      case (_, _, true) => StartDatePage
    }
  }

  override def isComplete(formData: RegistrationFormData): Boolean = formData.productionSites.isDefined

  override def show: Call = routes.ProductionSiteController.show()
}

case object WarehouseSitesPage extends MidJourneyPage {
  override def nextPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = ContactDetailsPage

  override def previousPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = formData.packaging match {
    case Some(p) if p.isPackager => ProductionSitesPage
    case Some(_) => StartDatePage
    case None => PackagePage
  }

  override def isComplete(formData: RegistrationFormData): Boolean = formData.secondaryWarehouses.isDefined

  override def show: Call = routes.WarehouseController.show()
}

case object ContactDetailsPage extends PageWithPreviousPage {
  override def previousPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = formData match {
    case f if f.isVoluntary => StartDatePage
    case _ => WarehouseSitesPage
  }

  override def isComplete(formData: RegistrationFormData): Boolean = formData.contactDetails.isDefined

  override def show: Call = routes.ContactDetailsController.show()
}

case object SmallProducerConfirmPage extends MidJourneyPage {

  override def nextPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = {
    if (config.taxStartDate isBefore LocalDate.now)
      StartDatePage
    else
      ProductionSitesPage
  }
  override def previousPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = formData.isImporter match {
    case Some(true) => ImportVolumePage
    case _ => ImportPage
  }

  override def isComplete(formData: RegistrationFormData): Boolean = formData.confirmedSmallProducer.isDefined

  override def show: Call = routes.SmallProducerConfirmController.show()
}