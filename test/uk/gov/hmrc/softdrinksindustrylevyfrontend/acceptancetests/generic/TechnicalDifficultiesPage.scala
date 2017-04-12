package uk.gov.hmrc.softdrinksindustrylevyfrontend.acceptancetests.generic

import uk.gov.hmrc.softdrinksindustrylevyfrontend.acceptancetests.pages.SDILPage


object TechnicalDifficultiesPage extends SDILPage {

  val pageTitle: String = "Sorry, we’re experiencing technical difficulties"

  override def isCurrentPage: Boolean = checkHeader("h1", pageTitle)
}
