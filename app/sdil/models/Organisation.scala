package sdil.models
import play.api.libs.json.{Format, Json}

class Organisation {

  case class EntityType(company: Boolean, soleProprietor: Boolean, llp: Boolean, unincorporatedBody: Boolean)

  object Organisation {
    implicit val organisationTypeFormat: Format[Organisation] = Json.format[Organisation]
  }
}
