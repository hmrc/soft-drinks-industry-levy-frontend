package sdil.controllers

import play.api.i18n.Messages
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsString, status}
org.mockito.ArgumentMatchers.{any, eq => matching}
import org.mockito.Mockito._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import sdil.models.{Or, RegistrationFormData}

class OrgTypeCpntrollerSpec {
  "GET /organisation-type" should {
    "always return 200 Ok and the organisation type page" in {
      val res = testController.displayOrgType()(FakeRequest())
      status(res) mustBe OK
      contentAsString(res) must include("What organisation type is your business?")
    }
  }
  lazy val testController = wire[OrgTypeController]
}
