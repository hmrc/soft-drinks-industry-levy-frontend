/*
 * Copyright 2017 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.softdrinksindustrylevyfrontend.generic

import uk.gov.hmrc.softdrinksindustrylevyfrontend.pages.SDILPage

object NginxFailurePage extends SDILPage {

  // TODO find a better way to implement this, don't have the page source to hand right now
  override def isCurrentPage: Boolean = pageSource.contains("nginx")

}
