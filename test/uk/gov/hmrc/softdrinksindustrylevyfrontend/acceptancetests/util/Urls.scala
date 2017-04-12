package uk.gov.hmrc.softdrinksindustrylevyfrontend.acceptancetests.util

object Urls extends Enumeration {
  val LOCAL = "http://localhost:9063"
  val DEV = "https://www-dev.tax.service.gov.uk"
  val PLATFORM_QA = "https://www-qa.tax.service.gov.uk"
  val PORTAL_QA = "https://ibt.hmrc.gov.uk"
  val STAGING = "https://www-staging.tax.service.gov.uk"
}
