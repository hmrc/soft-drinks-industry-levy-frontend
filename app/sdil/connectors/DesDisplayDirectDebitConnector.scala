package sdil.connectors

import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient

class DesDisplayDirectDebitConnector  (http: HttpClient, config: ServicesConfig) {

  lazy val desDisplayDirectDebitBaseUrl: String = s"${config.baseUrl("")}/cross-regime/direct-debits"


  def readDDUrl(sdilNumber: String): String =
    s"$desDisplayDirectDebitBaseUrl/zsdl/zsdl/${sdilNumber}"

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
