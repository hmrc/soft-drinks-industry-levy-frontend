package uk.gov.hmrc.softdrinksindustrylevyfrontend.acceptancetests.util

case class User(credId: String, utr: String, pass: String)

object Users {
  private val DEFAULT_DEV_USER = User("543212300016", "1097172564", "testing123")
  private val DEFAULT_STAGING_USER = User(RandomUtils.randString(12), "1097172564", "testing123")
  private val DEFAULT_QA_USER = User("SSTTPCREDID02", "2435657686", "")

  val DEFAULT_USER = {
    if (Env.isQA(Env.baseUrl)) DEFAULT_QA_USER
    else if (Env.isStaging(Env.baseUrl)) DEFAULT_STAGING_USER
    else DEFAULT_DEV_USER
  }
}


