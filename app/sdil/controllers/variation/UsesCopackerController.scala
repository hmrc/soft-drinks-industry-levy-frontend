package sdil.controllers.variation

import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent}
import sdil.actions.VariationAction
import sdil.config.AppConfig
import sdil.connectors.SoftDrinksIndustryLevyConnector
import sdil.controllers.{ProducerController, RadioFormController}
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import views.html.radio_button
import views.html.softdrinksindustrylevy.register.produce_worldwide

class UsesCopackerController(val messagesApi: MessagesApi,
                             sdilConnector: SoftDrinksIndustryLevyConnector,
                             cache: SessionCache,
                             variationAction: VariationAction)
                            (implicit config: AppConfig)
  extends FrontendController with I18nSupport {

  def show: Action[AnyContent] = variationAction { implicit request =>
    Ok(radio_button(RadioFormController.form, "packageCopack", routes.ProducerVariationsController.show()))
  }

}
