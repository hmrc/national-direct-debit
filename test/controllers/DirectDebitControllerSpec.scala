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

import base.SpecBase
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.http.Status.{BAD_REQUEST, OK}
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.Helpers.{contentAsJson, contentAsString, status}
import uk.gov.hmrc.nationaldirectdebit.controllers.DirectDebitController
import uk.gov.hmrc.nationaldirectdebit.models.requests.chris.{BankAddress, Country, DirectDebitSource, PaymentDateDetails, PaymentPlanType, PaymentsFrequency, PlanStartDateDetails, YourBankDetailsWithAuddisStatus}
import uk.gov.hmrc.nationaldirectdebit.models.requests.{ChrisSubmissionRequest, CreateDirectDebitRequest, GenerateDdiRefRequest, WorkingDaysOffsetRequest}
import uk.gov.hmrc.nationaldirectdebit.models.responses.{EarliestPaymentDateResponse, GenerateDdiRefResponse, RDSDatacacheResponse, RDSDirectDebitDetails}
import uk.gov.hmrc.nationaldirectdebit.services.DirectDebitService

import java.time.{LocalDate, LocalDateTime}
import scala.concurrent.Future

class DirectDebitControllerSpec extends SpecBase {

  "DirectDebitController" - {
    "retrieveDirectDebits method" - {
      "return 200 and a successful response when the max number of records is supplied" in new SetUp {
        when(mockDirectDebitService.retrieveDirectDebits(any())(any()))
          .thenReturn(Future.successful(testDataCacheResponse))

        val result: Future[Result] = controller.retrieveDirectDebits(firstRecordNumber = None, maxRecords = Some(2))(fakeRequest)

        status(result) mustBe OK
        contentAsJson(result) mustBe Json.toJson(testDataCacheResponse)
      }

      "return 200 and a successful response with 0 when the no value of max records is supplied" in new SetUp {
        when(mockDirectDebitService.retrieveDirectDebits(any())(any()))
          .thenReturn(Future.successful(testEmptyDataCacheResponse))

        val result: Future[Result] = controller.retrieveDirectDebits(firstRecordNumber = None, maxRecords = None)(fakeRequest)

        status(result) mustBe OK
        contentAsJson(result) mustBe Json.toJson(testEmptyDataCacheResponse)
      }
    }

    "createDirectDebit method" - {
      "return 200 and a successful response when the request is valid" in new SetUp {
        when(mockDirectDebitService.createDirectDebit(any())(any()))
          .thenReturn(Future.successful("test-reference"))

        val result: Future[Result] = controller.createDirectDebit()(fakeRequestWithJsonBody(Json.toJson(CreateDirectDebitRequest("some-reference"))))

        status(result) mustBe OK
        contentAsString(result) mustBe "test-reference"
      }

      "return 400 when the request is not valid" in new SetUp {
        val result: Future[Result] = controller.createDirectDebit()(fakeRequestWithJsonBody(Json.toJson("bad json")))

        status(result) mustBe BAD_REQUEST
      }
    }

    "getWorkingDaysOffset method" - {
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

    "submitToChris method" - {
      "return 200 and true when the request is valid" in new SetUp {
        val chrisSubmissionInstance = ChrisSubmissionRequest(
          serviceType = DirectDebitSource.TC,
          paymentPlanType = PaymentPlanType.TaxCreditRepaymentPlan,
          paymentFrequency = Some(PaymentsFrequency.Monthly),
          yourBankDetailsWithAuddisStatus = YourBankDetailsWithAuddisStatus(
            accountHolderName = "Test",
            sortCode = "123456",
            accountNumber = "12345678",
            auddisStatus = false,
            accountVerified = false
          ),
          planStartDate = Some(PlanStartDateDetails(
            enteredDate = LocalDate.of(2025, 9, 1),
            earliestPlanStartDate = "2025-09-01"
          )),
          planEndDate = None,
          paymentDate = Some(PaymentDateDetails(
            enteredDate = LocalDate.of(2025, 9, 15),
            earliestPaymentDate = "2025-09-01"
          )),
          yearEndAndMonth = None,
          bankDetailsAddress = BankAddress(
            lines = Seq("line 1"),
            town = "Town",
            country = Country("UK"),
            postCode = "NE5 2DH"
          ),
          ddiReferenceNo = "DDI123456789",
          paymentReference = Some("testReference"),
          bankName = "Barclays",
          totalAmountDue = Some(BigDecimal(200)),
          paymentAmount = Some(BigDecimal(100)),
          regularPaymentAmount = Some(BigDecimal(90)),
          calculation = None
        )

        val chrisSubmissionJson = Json.toJson(chrisSubmissionInstance)

        val result = controller.submitToChris()(fakeRequestWithJsonBody(chrisSubmissionJson))

        status(result) mustBe OK
        contentAsJson(result) mustBe Json.toJson(true)
      }

      "return 400 when the request JSON is invalid" in new SetUp {
        val badJson = Json.obj("invalid" -> "data")

        val result = controller.submitToChris()(fakeRequestWithJsonBody(badJson))

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

    val testResponseModel: EarliestPaymentDateResponse = EarliestPaymentDateResponse(LocalDate.of(2025, 12, 13))
    val testRequestModel: WorkingDaysOffsetRequest = WorkingDaysOffsetRequest(LocalDate.of(2025, 12, 5), 8)
    val testDdiRefRequestModel: GenerateDdiRefRequest = GenerateDdiRefRequest("12345")
    val testEmptyDataCacheResponse: RDSDatacacheResponse = RDSDatacacheResponse(directDebitCount = 0, directDebitList = Seq.empty)
    val testDataCacheResponse: RDSDatacacheResponse = RDSDatacacheResponse(directDebitCount = 2,
      directDebitList = Seq(
        RDSDirectDebitDetails(ddiRefNumber = "testRef", submissionDateTime = LocalDateTime.of(2025, 12, 12, 12, 12), bankSortCode = "testCode", bankAccountNumber = "testNumber", bankAccountName = "testName", auDdisFlag = true, numberOfPayPlans = 1),
        RDSDirectDebitDetails(ddiRefNumber = "testRef", submissionDateTime = LocalDateTime.of(2025, 12, 12, 12, 12), bankSortCode = "testCode", bankAccountNumber = "testNumber", bankAccountName = "testName", auDdisFlag = true, numberOfPayPlans = 1)
      ))

    val controller = new DirectDebitController(fakeAuthAction, mockDirectDebitService, cc)
  }
}
