/*
 * Copyright 2021 HM Revenue & Customs
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

package sdil.controllers

import scala.language.higherKinds

import ltbs.uniform._, validation.Rule
import sdil.models._, backend._, retrieved._
import sdil.connectors.SoftDrinksIndustryLevyConnector
import cats.implicits._
import izumi.reflect.Tag
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import play.api.mvc._
import play.api.i18n._
import sdil.config.AppConfig
import scala.concurrent.Future
import sdil.actions.RegisteredAction
import play.api.Logger
import scala.concurrent.ExecutionContext
import sdil.uniform.SaveForLaterPersistenceNew
import sdil.config.RegistrationFormDataCache
import ltbs.uniform.interpreters.playframework.PersistenceEngine
import sdil.actions.AuthorisedRequest
import sdil.actions.RegisteredRequest

class ReturnsControllerNew(
  mcc: MessagesControllerComponents,
  val config: AppConfig,
  val ufViews: views.uniform.Uniform,
  registeredAction: RegisteredAction,
  sdilConnector: SoftDrinksIndustryLevyConnector,
  cache: RegistrationFormDataCache
)(implicit ec: ExecutionContext)
    extends FrontendController(mcc) with I18nSupport with HmrcPlayInterpreter {

  val logger: Logger = Logger(this.getClass())
  override def defaultBackLink = "/soft-drinks-industry-levy"

  implicit lazy val persistence =
    SaveForLaterPersistenceNew[RegisteredRequest[AnyContent]](_.sdilEnrolment.value)("returns", cache.shortLiveCache)

  def index(year: Int, quarter: Int, nilReturn: Boolean, id: String): Action[AnyContent] = registeredAction.async {
    implicit request =>
      import CheckYourAnswers._

      val sdilRef = request.sdilEnrolment.value
      val period = ReturnPeriod(year, quarter)

      (for {
        subscription   <- sdilConnector.retrieveSubscription(sdilRef).map { _.get }
        pendingReturns <- sdilConnector.returns_pending(subscription.utr)
        broughtForward <- if (config.balanceAllEnabled)
                           sdilConnector.balanceHistory(sdilRef, withAssessment = false).map { x =>
                             extractTotal(listItemsWithTotal(x))
                           } else sdilConnector.balance(sdilRef, withAssessment = false)
        isSmallProd <- sdilConnector.checkSmallProducerStatus(sdilRef, period).flatMap {
                        case Some(x) =>
                          x // the given sdilRef matches a customer that was a small producer at some point in the quarter
                        case None => false
                      }
        r <- if (pendingReturns.contains(period))
              interpret(ReturnsControllerNew.journey(None, subscription, broughtForward, isSmallProd)).run(id) {
                case (a: SdilReturn, b: ReturnsVariation) =>
                  // do some logic
                  Future.successful(Ok("???"))
              } else
              Redirect(routes.ServicePageController.show()).pure[Future]
      } yield r) recoverWith {
        case t: Throwable => {
          logger.error(s"Exception occurred while retrieving pendingReturns for sdilRef =  $sdilRef", t)
          Redirect(routes.ServicePageController.show()).pure[Future]
        }
      }

      Future.successful(Ok("!"))
  }
}

object ReturnsControllerNew {

  // TODO: Move to config file
  val costLower = BigDecimal("0.18")
  val costHigher = BigDecimal("0.24")

  private[controllers] def journey(
    default: Option[SdilReturn] = None,
    subscription: RetrievedSubscription,
    broughtForward: BigDecimal,
    isSmallProd: Boolean
  ) = {

    case object ChangeRegistration

    for {
      ownBrands <- askEmptyOption[(Long, Long)](
                    "own-brands-packaged-at-own-sites",
                    default = default.map { _.packLarge }
                  ) emptyUnless !subscription.activity.smallProducer
      contractPacked <- askEmptyOption[(Long, Long)](
                         "packaged-as-a-contract-packer"
                       ) emptyUnless !subscription.activity.smallProducer
      smallProds <- askList(
                     "small-producer-details",
                     validation = Rule.nonEmpty[List[SmallProducer]],
                     default = default.map { _.packSmall }
                   ) {
                     case (index: Option[Int], existing: List[SmallProducer]) =>
                       ask[SmallProducer]("small-producer", default = index.map(existing))
                   } emptyUnless ask[Boolean]("exemptions-for-small-producers", default = default.map {
                     _.packSmall.nonEmpty
                   })
      imports <- askEmptyOption[(Long, Long)]("brought-into-uk", default.map { _.importLarge })
      importsSmall <- askEmptyOption[(Long, Long)]("brought-into-uk-from-small-producers", default.map {
                       _.importSmall
                     })
      exportCredits <- askEmptyOption[(Long, Long)]("claim-credits-for-exports", default.map { _.export })
      wastage       <- askEmptyOption[(Long, Long)]("claim-credits-for-lost-damaged", default.map { _.wastage })
      sdilReturn = SdilReturn(ownBrands, contractPacked, smallProds, imports, importsSmall, exportCredits, wastage)
      isNewImporter = !sdilReturn.totalImported.isEmpty && !subscription.activity.importer
      isNewPacker = !sdilReturn.totalPacked.isEmpty && !subscription.activity.contractPacker
//      _               <- tell("return-change-registration", ChangeRegistration) when isNewImporter || isNewPacker
      newPackingSites <- (
                          for {
                            firstPackingSite <- ask[Boolean]("pack-at-business-address-in-return") flatMap {
                                                 case true =>
                                                   pure(Site.fromAddress(Address.fromUkAddress(subscription.address)))
                                                 case false => ask[Site]("first-production-site")
                                               }
                            packingSites <- askList[Site](
                                             "production-site-details",
                                             default = Some(firstPackingSite :: Nil),
                                             validation = Rule.nonEmpty[List[Site]]
                                           ) {
                                             case (index: Option[Int], existing: List[Site]) =>
                                               ask[Site]("site", default = index.map(existing))
                                           }
                          } yield packingSites
                        ) when isNewPacker && subscription.productionSites.isEmpty
      newWarehouses <- (for {
                        addWarehouses <- ask[Boolean]("ask-secondary-warehouses-in-return")
                        warehouses <- askList[Site](
                                       "secondary-warehouse-details",
                                       validation = Rule.nonEmpty[List[Site]]
                                     ) {
                                       case (index: Option[Int], existing: List[Site]) =>
                                         ask[Site]("site", default = index.map(existing))
                                     } emptyUnless addWarehouses
                      } yield warehouses) when isNewImporter && subscription.warehouseSites.isEmpty
      variation = ReturnsVariation(
        orgName = subscription.orgName,
        ppobAddress = subscription.address,
        importer = (isNewImporter, (sdilReturn.totalImported).combineN(4)),
        packer = (isNewPacker, (sdilReturn.totalPacked).combineN(4)),
        warehouses = newWarehouses.getOrElse(List.empty[Site]),
        packingSites = newPackingSites.getOrElse(List.empty[Site]),
        phoneNumber = subscription.contact.phoneNumber,
        email = subscription.contact.email,
        taxEstimation = taxEstimation(sdilReturn)
      )
      // _ <- tell("cya", variation)
    } yield (sdilReturn, variation)
  }

  def taxEstimation(r: SdilReturn): BigDecimal = {
    val t = r.packLarge |+| r.importLarge |+| r.ownBrand
    (t._1 * costLower |+| t._2 * costHigher) * 4
  }

}
