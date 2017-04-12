package uk.gov.hmrc.softdrinksindustrylevyfrontend.acceptancetests.generic

import uk.gov.hmrc.softdrinksindustrylevyfrontend.acceptancetests.pages.SDILPage


object UnavailablePage extends SDILPage{

  val pageTitle: String = "Please call us"

  override def isCurrentPage: Boolean = checkHeader("h1", pageTitle)
}
