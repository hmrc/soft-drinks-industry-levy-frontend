/*
 * Copyright 2018 HM Revenue & Customs
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

package sdil.filters

import akka.stream.Materializer
import play.api.mvc.{Filter, RequestHeader, Result}
import sdil.config.AppConfig
import uk.gov.hmrc.play.bootstrap.http.FrontendErrorHandler

import scala.concurrent.Future

class VariationsFilter(errorHandler: FrontendErrorHandler)
                      (implicit config: AppConfig, val mat: Materializer) extends Filter {
  override def apply(f: RequestHeader => Future[Result])(rh: RequestHeader): Future[Result] = {
      f(rh)
  }
}
