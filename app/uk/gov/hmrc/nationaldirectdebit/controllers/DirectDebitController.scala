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
import play.api.Logging
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.nationaldirectdebit.actions.AuthAction
import uk.gov.hmrc.nationaldirectdebit.models.requests.{ChrisSubmissionRequest, GenerateDdiRefRequest, WorkingDaysOffsetRequest}
import uk.gov.hmrc.nationaldirectdebit.services.{ChrisService, DirectDebitService}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.ExecutionContext

class DirectDebitController @Inject()(
                                       authorise: AuthAction,
                                       service: DirectDebitService,
                                       chrisService: ChrisService,
                                       val cc: ControllerComponents
                                     )(implicit ec: ExecutionContext) extends BackendController(cc) with Logging {

  def retrieveDirectDebits(): Action[AnyContent] =
    authorise.async {
      implicit request =>
        service.retrieveDirectDebits().map { response =>
          Ok(Json.toJson(response))
        }
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

  def retrieveDirectDebitPaymentPlans(directDebitReference: String): Action[AnyContent] =
    authorise.async {
      implicit request =>
        service.retrieveDirectDebitPaymentPlans(directDebitReference).map { response =>
          Ok(Json.toJson(response))
        }
    }


  def submitToChris(): Action[JsValue] =
    authorise(parse.json).async { implicit request =>
      withJsonBody[ChrisSubmissionRequest] { chrisRequest =>

        logger.info(
          s"""|
             |Chris Submission Request received:
              |${Json.prettyPrint(Json.toJson(chrisRequest))}
              |""".stripMargin
        )

        chrisService.submitToChris(chrisRequest, request.credId, request.affinityGroup, request)
          .map { response =>
            // Success: return 200 with response
            logger.info(s"ChRIS submission successful for request: ${chrisRequest.ddiReferenceNo}")
            Ok(Json.obj(
              "success" -> true,
              "response" -> response
            ))
          }
          .recover { case ex =>
            // Log full stack trace
            logger.error(s"ChRIS submission failed for request: ${chrisRequest.ddiReferenceNo}", ex)

            // Return structured error JSON to frontend
            InternalServerError(Json.obj(
              "success" -> false,
              "message" -> s"CHRIS submission failed: ${ex.getMessage}",
              "exception" -> ex.getClass.getSimpleName
            ))
          }
      }
    }

  def retrievePaymentPlanDetails(directDebitReference: String, paymentPlanReference: String): Action[AnyContent] =
    authorise.async {
      implicit request =>
        service.retrievePaymentPlanDetails(directDebitReference, paymentPlanReference).map { response =>
          Ok(Json.toJson(response))
        }
    }
}
