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

  protected def showStartDate(implicit config: AppConfig): Boolean = LocalDate.now isAfter config.taxStartDate
}

sealed trait PageWithPreviousPage extends Page {
  def previousPage(formData: RegistrationFormData)(implicit config: AppConfig): Page

  override def expectedPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = {
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
    case Some(_) => ProducerPage
    case None => OrgTypePage
  }

  override def previousPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = VerifyPage

  override def isComplete(formData: RegistrationFormData): Boolean = formData.organisationType.isDefined

  override def show: Call = routes.OrganisationTypeController.show
}

case object ProducerPage extends MidJourneyPage {

  override def nextPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = formData.producer match {
    case Some(Producer(false, _)) => PackageCopackPage
    case Some(Producer(true, Some(false))) => CopackedPage
    case _ => PackageOwnUkPage
  }

  override def previousPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = OrgTypePage

  override def isComplete(formData: RegistrationFormData): Boolean = formData.producer.isDefined

  override def show: Call = routes.ProducerController.show()
}

case object CopackedPage extends MidJourneyPage {
  override def nextPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = formData.usesCopacker match {
    case Some(_) => PackageOwnUkPage
    case None => CopackedPage
  }

  override def previousPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = ProducerPage

  override def isComplete(formData: RegistrationFormData): Boolean = formData.usesCopacker.isDefined

  override def show: Call = routes.RadioFormController.show("copacked")
}

case object PackageOwnUkPage extends MidJourneyPage {
  override def nextPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = formData.isPackagingForSelf match {
    case Some(true) => PackageOwnVolPage
    case _ => PackageCopackPage
  }

  override def previousPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = formData.producer match {
    case Some(Producer(true, Some(true))) => ProducerPage
    case None => ProducerPage
    case _ => CopackedPage
  }

  override def isComplete(formData: RegistrationFormData): Boolean = formData.isPackagingForSelf.isDefined

  override def show: Call = routes.RadioFormController.show("packageOwnUk")
}

case object PackageOwnVolPage extends MidJourneyPage {
  override def nextPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = PackageCopackPage

  override def previousPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = PackageOwnUkPage

  override def isComplete(formData: RegistrationFormData): Boolean = formData.volumeForOwnBrand.isDefined

  override def show: Call = routes.LitreageController.show("packageOwnVol")
}

case object PackageCopackPage extends MidJourneyPage {
  override def nextPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = formData.packagesForOthers match {
    case Some(true) => PackageCopackVolPage
    case Some(false) => ImportPage
    case None => PackageCopackPage
  }

  override def previousPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = formData match {
    case fd if !fd.producer.exists(_.isProducer) => ProducerPage
    case fd if !fd.isPackagingForSelf.getOrElse(false) => PackageOwnUkPage
    case fd if fd.isPackagingForSelf.getOrElse(false) => PackageOwnVolPage
  }

  override def isComplete(formData: RegistrationFormData): Boolean = formData.packagesForOthers.isDefined

  override def show: Call = routes.RadioFormController.show("packageCopack")
}

case object PackageCopackVolPage extends MidJourneyPage {
  override def nextPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = ImportPage

  override def previousPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = PackageCopackPage

  override def isComplete(formData: RegistrationFormData): Boolean = formData.volumeForCustomerBrands.isDefined

  override def show: Call = routes.LitreageController.show("packageCopackVol")
}

case object ImportPage extends MidJourneyPage {
  override def nextPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = formData.isImporter match {
    case Some(true) => ImportVolumePage
    case Some(false) => RegistrationTypePage
    case None => ImportPage
  }

  override def previousPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = formData.packagesForOthers match {
    case Some(true) => PackageCopackVolPage
    case _ => PackageCopackPage
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

  override def nextPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = {
    if (formData.isVoluntary) {
      ContactDetailsPage
    } else if (formData.hasPackagingSites) {
      ProductionSitesPage
    } else {
      WarehouseSitesPage
    }
  }

  override def previousPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = formData match {
    case form if form.isImporter.contains(true) => ImportVolumePage
    case _ => ImportPage
  }

  override def isComplete(formData: RegistrationFormData): Boolean = formData.startDate.isDefined

  override def show: Call = routes.StartDateController.show()
}

case object ProductionSitesPage extends MidJourneyPage {
  override def nextPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = WarehouseSitesPage

  override def previousPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = {
    if (showStartDate) StartDatePage else StartDatePage.previousPage(formData)
  }

  override def isComplete(formData: RegistrationFormData): Boolean = formData.productionSites.isDefined

  override def show: Call = routes.ProductionSiteController.show()
}

case object WarehouseSitesPage extends MidJourneyPage {
  override def nextPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = ContactDetailsPage

  override def previousPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = {
    formData match {
      case fd if fd.hasPackagingSites => ProductionSitesPage
      case fd if fd.producer.flatMap(_.isLarge).forall(_ == false)
                 && fd.isImporter.getOrElse(false)
                 && !showStartDate =>
                   ImportVolumePage
      case _ if showStartDate => StartDatePage
      case _ => StartDatePage.previousPage(formData)
    }
  }

  override def isComplete(formData: RegistrationFormData): Boolean = formData.secondaryWarehouses.isDefined

  override def show: Call = routes.WarehouseController.show()
}

case object ContactDetailsPage extends PageWithPreviousPage {
  override def previousPage(formData: RegistrationFormData)(implicit config: AppConfig): Page = formData match {
    case f if !f.isVoluntary => WarehouseSitesPage
    case f if f.isVoluntary => ImportPage
    case _ if showStartDate => StartDatePage
    case _ => StartDatePage.previousPage(formData)
  }

  override def isComplete(formData: RegistrationFormData): Boolean = formData.contactDetails.isDefined

  override def show: Call = routes.ContactDetailsController.show()
}
