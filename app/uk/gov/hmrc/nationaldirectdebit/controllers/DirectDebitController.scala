/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.nationaldirectdebit.controllers

import com.google.inject.Inject
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.nationaldirectdebit.actions.AuthAction
import uk.gov.hmrc.nationaldirectdebit.models.requests.{CreateDirectDebitRequest, GenerateDdiRefRequest, WorkingDaysOffsetRequest}
import uk.gov.hmrc.nationaldirectdebit.services.DirectDebitService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.ExecutionContext

class DirectDebitController @Inject()(
                                       authorise: AuthAction,
                                       service: DirectDebitService,
                                       val cc: ControllerComponents
                                     )(implicit ec: ExecutionContext) extends BackendController(cc) {


  def retrieveDirectDebits(firstRecordNumber: Option[Int], maxRecords: Option[Int]): Action[AnyContent] =
    authorise.async {
      implicit request =>
        service.retrieveDirectDebits(maxRecords.getOrElse(0)).map { response =>
          Ok(Json.toJson(response))
        }
    }

  def createDirectDebit(): Action[JsValue] =
    authorise(parse.json).async:
      implicit request =>
        withJsonBody[CreateDirectDebitRequest] { request =>
          service.createDirectDebit(request).map(
            response =>
              Ok(response)
          )
        }

  def getWorkingDaysOffset: Action[JsValue] =
    authorise(parse.json).async:
      implicit request =>
        withJsonBody[WorkingDaysOffsetRequest] { request =>
          service.getWorkingDaysOffset(request).map { response =>
            Ok(Json.toJson(response))
          }
        }

  def generateDdiReference(): Action[JsValue] =
    authorise(parse.json).async:
      implicit request =>
        withJsonBody[GenerateDdiRefRequest] { request =>
          service.generateDdiReference(request).map { response =>
            Ok(Json.toJson(response))
          }
        }

  def retrieveDirectDebitPaymentPlans(paymentReference: String,
                                      firstRecordNumber: Option[Int],
                                      maxRecords: Option[Int]): Action[AnyContent] =
    authorise.async {
      implicit request =>
        service.retrieveDirectDebitPaymentPlans(paymentReference, maxRecords.getOrElse(0)).map { response =>
          Ok(Json.toJson(response))
        }
    }
}
