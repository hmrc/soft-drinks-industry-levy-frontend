package sdil.connectors

import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

class DirectDebitBackendConnector (http: HttpClient, config: ServicesConfig) {

  lazy val directDebitBaseUrl: String = s"${config.baseUrl("pay-api")}/pay-api"

  def getSdilPayLink(
                      spjRequest: SpjRequestBtaSdil)(implicit headerCarrier: HeaderCarrier, ec: ExecutionContext): Future[NextUrl] =
    http.POST[SpjRequestBtaSdil, NextUrl](
      s"$payApiBaseUrl/bta/sdil/journey/start",
      spjRequest,
      Seq("X-Session-Id" -> headerCarrier.sessionId.get.value))
}

final case class SpjRequestBtaSdil(
                                    reference: String,
                                    amountInPence: Long,
                                    returnUrl: String,
                                    backUrl: String
                                  )

object SpjRequestBtaSdil {
  implicit val format: Format[SpjRequestBtaSdil] = Json.format[SpjRequestBtaSdil]
}

final case class NextUrl(nextUrl: String)

object NextUrl {
  implicit val nextUrlFormat: Format[NextUrl] = Json.format[NextUrl]
}
