package sdil.controllers

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.Injector
import play.api.mvc.MessagesControllerComponents
import play.api.test.Helpers.stubMessagesControllerComponents
import play.api.test.{Injecting, StubMessagesFactory}
import sdil.actions.AuthorisedAction
import sdil.config.{AppConfig, RegistrationFormDataCache}
import sdil.connectors.SoftDrinksIndustryLevyConnector
import views.Views

import scala.concurrent.ExecutionContext

class RedirectToNewServiceTests extends AnyFreeSpec
  with GuiceOneAppPerSuite
  with Matchers
  with Injecting
  with StubMessagesFactory
  with ScalaFutures
  with IntegrationPatience
  with MockitoSugar {

  lazy val injector: Injector = app.injector
  implicit val ec: ExecutionContext = injector.instanceOf[ExecutionContext]
  val views = injector.instanceOf[Views]
  lazy val mcc: MessagesControllerComponents =
    stubMessagesControllerComponents()
  val mockCache = mock[RegistrationFormDataCache]
  val mockAuthAction = mock[AuthorisedAction]
  val mockSdilConnector = mock[SoftDrinksIndustryLevyConnector]
  val mockConfig = mock[AppConfig]

  val identifyController = new IdentifyController(mcc, mockCache, mockAuthAction, mockSdilConnector, views)(mockConfig, ec)

  "Identify.start" - {
    "when redirectToNewRegistrationsEnabled is true" - {
      "and the cache is empty" - {
        "should redirect to sdilRegistration" in {

        }
      }
    }
  }


}
