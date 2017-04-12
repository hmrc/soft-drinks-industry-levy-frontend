package uk.gov.hmrc.softdrinksindustrylevyfrontend.acceptancetests

import uk.gov.hmrc.softdrinksindustrylevyfrontend.acceptancetests.pages.TestPage


class MainSpec extends BaseFeatureSpec {

  feature("Test feature") {
    scenario("Test scenario") {
      Given("I navigate to the google website")
      TestPage.goToGoogle()
    }
  }

}
