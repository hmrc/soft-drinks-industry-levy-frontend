package uk.gov.hmrc.softdrinksindustrylevyfrontend.acceptancetests.generic

import uk.gov.hmrc.softdrinksindustrylevyfrontend.acceptancetests.pages.SDILPage

object NginxFailurePage extends SDILPage {

  // TODO find a better way to implement this, don't have the page source to hand right now
  override def isCurrentPage: Boolean = pageSource.contains("nginx")

}
