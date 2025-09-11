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

package controllers

import actions.FakeAuthAction
import base.SpecBase
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.http.Status.{BAD_REQUEST, OK}
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.Helpers.{contentAsJson, status}
import uk.gov.hmrc.nationaldirectdebit.controllers.DirectDebitController
import uk.gov.hmrc.nationaldirectdebit.models.requests.chris.*
import uk.gov.hmrc.nationaldirectdebit.models.requests.{ChrisSubmissionRequest, CreateDirectDebitRequest, GenerateDdiRefRequest, WorkingDaysOffsetRequest}
import uk.gov.hmrc.nationaldirectdebit.models.requests.{GenerateDdiRefRequest, WorkingDaysOffsetRequest}
import uk.gov.hmrc.nationaldirectdebit.models.responses.{EarliestPaymentDateResponse, GenerateDdiRefResponse, RDSDatacacheResponse, RDSDirectDebitDetails}
import uk.gov.hmrc.nationaldirectdebit.services.{ChrisService, DirectDebitService}

import java.time.{LocalDate, LocalDateTime}
import scala.concurrent.Future

class DirectDebitControllerSpec extends SpecBase {

  "DirectDebitController" - {

    "retrieveDirectDebits method" - {
      "return 200 and a successful response when the max number of records is supplied" in new SetUp {
        when(mockDirectDebitService.retrieveDirectDebits()(any()))
          .thenReturn(Future.successful(testDataCacheResponse))

        val result: Future[Result] = controller.retrieveDirectDebits()(fakeRequest)

        status(result) mustBe OK
        contentAsJson(result) mustBe Json.toJson(testDataCacheResponse)
      }

      "return 200 and a successful response with 0 when the no value of max records is supplied" in new SetUp {
        when(mockDirectDebitService.retrieveDirectDebits()(any()))
          .thenReturn(Future.successful(testEmptyDataCacheResponse))

        val result: Future[Result] = controller.retrieveDirectDebits()(fakeRequest)

        status(result) mustBe OK
        contentAsJson(result) mustBe Json.toJson(testEmptyDataCacheResponse)
      }
    }

    "AddFutureWorkingDaysOffset method" - {
      "return 200 and a successful response when the request is valid" in new SetUp {
        when(mockDirectDebitService.getWorkingDaysOffset(any())(any()))
          .thenReturn(Future.successful(testResponseModel))

        val result: Future[Result] = controller.getWorkingDaysOffset()(fakeRequestWithJsonBody(Json.toJson(testRequestModel)))

        status(result) mustBe OK
        contentAsJson(result) mustBe Json.toJson(testResponseModel)
      }

      "return 400 when the request is not valid" in new SetUp {
        val result: Future[Result] = controller.getWorkingDaysOffset()(fakeRequestWithJsonBody(Json.toJson("invalid json")))

        status(result) mustBe BAD_REQUEST
      }
    }

    "generateDdiReference method" - {
      "return 200 and a successful response when the request is valid" in new SetUp {
        when(mockDirectDebitService.generateDdiReference(any())(any()))
          .thenReturn(Future.successful(GenerateDdiRefResponse("123")))

        val result: Future[Result] = controller.generateDdiReference()(fakeRequestWithJsonBody(Json.toJson(testDdiRefRequestModel)))

        status(result) mustBe OK
        contentAsJson(result) mustBe Json.toJson(GenerateDdiRefResponse("123"))
      }

      "return 400 when the request is not valid" in new SetUp {
        val result: Future[Result] = controller.generateDdiReference()(fakeRequestWithJsonBody(Json.toJson(789)))

        status(result) mustBe BAD_REQUEST
      }
    }
  }

  class SetUp {
    val mockDirectDebitService: DirectDebitService = mock[DirectDebitService]
    val mockChrisService: ChrisService = mock[ChrisService]

    val testResponseModel: EarliestPaymentDateResponse = EarliestPaymentDateResponse(LocalDate.of(2025, 12, 13))
    val testRequestModel: WorkingDaysOffsetRequest = WorkingDaysOffsetRequest(LocalDate.of(2025, 12, 5), 8)
    val testDdiRefRequestModel: GenerateDdiRefRequest = GenerateDdiRefRequest("12345")
    val testEmptyDataCacheResponse: RDSDatacacheResponse = RDSDatacacheResponse(0, Seq.empty)
    val testDataCacheResponse: RDSDatacacheResponse = RDSDatacacheResponse(
      2,
      Seq(
        RDSDirectDebitDetails("testRef", LocalDateTime.of(2025,12,12,12,12),"testCode","testNumber","testName",true,1),
        RDSDirectDebitDetails("testRef", LocalDateTime.of(2025,12,12,12,12),"testCode","testNumber","testName",true,1)
      )
    )

    val fakeAuthAction = new FakeAuthAction(
      bodyParsers = bodyParsers,
      testCredId = "cred-123",
      testAffinity = "Individual"
    )

    val controller = new DirectDebitController(fakeAuthAction, mockDirectDebitService, mockChrisService, cc)
  }

}
