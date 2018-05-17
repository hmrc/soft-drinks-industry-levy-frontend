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

import play.api.mvc.Call
import sdil.controllers.routes

sealed trait Page {
  def isVisible(implicit formData: RegistrationFormData): Boolean

  def isComplete(implicit formData: RegistrationFormData): Boolean

  def isIncomplete(implicit formData: RegistrationFormData): Boolean = !isComplete(formData)

  def show: Call
}

case object IdentifyPage extends Page {
  override def isVisible(implicit formData: RegistrationFormData): Boolean = true

  override def isComplete(implicit formData: RegistrationFormData): Boolean = true

  override def show: Call = routes.IdentifyController.show()
}

case object VerifyPage extends Page {
  override def isVisible(implicit formData: RegistrationFormData): Boolean = true

  override def isComplete(implicit formData: RegistrationFormData): Boolean = formData.verify.isDefined

  override def show: Call = routes.VerifyController.show()
}

case object OrganisationTypePage extends Page {
  override def isVisible(implicit formData: RegistrationFormData): Boolean = true

  override def isComplete(implicit formData: RegistrationFormData): Boolean = formData.organisationType.isDefined

  override def show: Call = routes.OrganisationTypeController.show()
}

case object ProducerPage extends Page {
  override def isVisible(implicit formData: RegistrationFormData): Boolean = true

  override def isComplete(implicit formData: RegistrationFormData): Boolean = formData.producer.isDefined

  override def show: Call = routes.ProducerController.show()
}

case object CopackedPage extends Page {
  override def isVisible(implicit formData: RegistrationFormData): Boolean = {
    formData.producer.flatMap(_.isLarge).contains(false)
  }

  override def isComplete(implicit formData: RegistrationFormData): Boolean = formData.usesCopacker.isDefined

  override def show: Call = routes.RadioFormController.show("copacked")
}

case object PackageOwnUkPage extends Page {
  override def isVisible(implicit formData: RegistrationFormData): Boolean = {
    formData.producer.exists(_.isProducer)
  }

  override def isComplete(implicit formData: RegistrationFormData): Boolean = formData.isPackagingForSelf.isDefined

  override def show: Call = routes.RadioFormController.show("packageOwnUk")
}

case object PackageOwnVolPage extends Page {
  override def isVisible(implicit formData: RegistrationFormData): Boolean = {
    formData.isPackagingForSelf.contains(true)
  }

  override def isComplete(implicit formData: RegistrationFormData): Boolean = formData.volumeForOwnBrand.isDefined

  override def show: Call = routes.LitreageController.show("packageOwnVol")
}

case object PackageCopackPage extends Page {
  override def isVisible(implicit formData: RegistrationFormData): Boolean = true

  override def isComplete(implicit formData: RegistrationFormData): Boolean = formData.packagesForOthers.isDefined

  override def show: Call = routes.RadioFormController.show("packageCopack")
}

case object PackageCopackVolPage extends Page {
  override def isVisible(implicit formData: RegistrationFormData): Boolean = {
    formData.packagesForOthers.contains(true)
  }

  override def isComplete(implicit formData: RegistrationFormData): Boolean = formData.volumeForCustomerBrands.isDefined

  override def show: Call = routes.LitreageController.show("packageCopackVol")
}

case object ImportPage extends Page {
  override def isVisible(implicit formData: RegistrationFormData): Boolean = true

  override def isComplete(implicit formData: RegistrationFormData): Boolean = formData.isImporter.isDefined

  override def show: Call = routes.RadioFormController.show("import")
}

case object ImportVolumePage extends Page {
  override def isVisible(implicit formData: RegistrationFormData): Boolean = {
    formData.isImporter.contains(true)
  }

  override def isComplete(implicit formData: RegistrationFormData): Boolean = formData.importVolume.isDefined

  override def show: Call = routes.LitreageController.show("importVolume")
}

case object DoNotRegisterPage extends Page {
  override def isVisible(implicit formData: RegistrationFormData): Boolean = formData.isNotAllowedToRegister

  override def isComplete(implicit formData: RegistrationFormData): Boolean = false

  override def show: Call = routes.RegistrationNotRequiredController.show()
}

case object StartDatePage extends Page {
  override def isVisible(implicit formData: RegistrationFormData): Boolean = {
    !formData.isVoluntary
  }

  override def isComplete(implicit formData: RegistrationFormData): Boolean = formData.startDate.isDefined

  override def show: Call = routes.StartDateController.show()
}

case object ProductionSitesPage extends Page {
  override def isVisible(implicit formData: RegistrationFormData): Boolean = {
    formData.hasPackagingSites
  }

  override def isComplete(implicit formData: RegistrationFormData): Boolean = formData.productionSites.exists(_.nonEmpty)

  override def show: Call = routes.ProductionSiteController.show()
}

case object WarehouseSitesPage extends Page {
  override def isVisible(implicit formData: RegistrationFormData): Boolean = !formData.isVoluntary

  override def isComplete(implicit formData: RegistrationFormData): Boolean = formData.secondaryWarehouses.isDefined

  override def show: Call = routes.WarehouseController.show()
}

case object ContactDetailsPage extends Page {
  override def isVisible(implicit formData: RegistrationFormData): Boolean = true

  override def isComplete(implicit formData: RegistrationFormData): Boolean = formData.contactDetails.isDefined

  override def show: Call = routes.ContactDetailsController.show()
}

case object DeclarationPage extends Page {
  override def isVisible(implicit formData: RegistrationFormData): Boolean = true

  override def isComplete(implicit formData: RegistrationFormData): Boolean = true

  override def show: Call = routes.DeclarationController.show()
}
