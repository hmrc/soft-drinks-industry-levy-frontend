package sdil.connectors

import play.api.libs.json.{Format, Json}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.config.{RunMode, ServicesConfig}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

class PayApiConnector(
                       http: HttpClient,
                       environment: Environment,
                       val runModeConfiguration: Configuration,
                       val runMode: RunMode) extends ServicesConfig(runModeConfiguration, runMode) {


  lazy val payApiBaseUrl: String = baseUrl("payments.payApiBaseUrl")
  lazy val payFrontendBaseUrl: String = baseUrl("payments.payFrontendBaseUrl")



  def getSdilPayLink(spjRequest: SpjRequestBtaSdil)(implicit headerCarrier: HeaderCarrier, ec: ExecutionContext): Future[NextUrl] = {
    http.POST[SpjRequestBtaSdil, NextUrl](s"$payApiBaseUrl/bta/epaye/bill/journey/start", spjRequest)
      .recover({
        case _: Exception =>
          NextUrl(s"$payFrontendBaseUrl/service-unavailable")
      })
  }

}

final case class SpjRequestBtaSdil(
                                    prn:           String,
                                    amountInPence: Long,
                                    returnUrl:     String,
                                    backUrl:       String
                                  )

object SpjRequestBtaSdil {
  implicit val format: Format[SpjRequestBtaSdil] = Json.format[SpjRequestBtaSdil]
}

final case class NextUrl(nextUrl: String)

object NextUrl {
  implicit val nextUrlFormat: Format[NextUrl] = Json.format[NextUrl]
}
