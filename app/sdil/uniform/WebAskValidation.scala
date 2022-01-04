/*
 * Copyright 2022 HM Revenue & Customs
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

package sdil.uniform

import ltbs.uniform.common.web.WebAsk
import play.api.i18n.MessagesApi
import play.twirl.api.Html
import sdil.config.AppConfig
import sdil.models.Address
import sdil.controllers.HmrcPlayInterpreter
import views.uniform.Uniform
import scala.language.higherKinds

object WebAskValidation extends HmrcPlayInterpreter {
  val myAskAddress: WebAsk[Html, Address] = implicitly[WebAsk[Html, Address]]
  override val config: AppConfig = ???

  override def messagesApi: MessagesApi = ???

  override def ufViews: Uniform = ???

  override def defaultBackLink: String = ???
}
