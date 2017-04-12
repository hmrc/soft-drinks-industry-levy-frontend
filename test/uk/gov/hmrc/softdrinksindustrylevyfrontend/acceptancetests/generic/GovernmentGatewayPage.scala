package uk.gov.hmrc.softdrinksindustrylevyfrontend.acceptancetests.generic

trait GovernmentGatewayPage extends WebPage {

  override val url: String = ""

  def userId: TextField = textField("userId")
  def password: PasswordField = pwdField("password")

}
