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

package services

import base.SpecBase
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar.mock
import uk.gov.hmrc.nationaldirectdebit.connectors.DirectDebitConnector
import uk.gov.hmrc.nationaldirectdebit.models.requests.{GenerateDdiRefRequest, PaymentPlanDuplicateCheckRequest, WorkingDaysOffsetRequest}
import uk.gov.hmrc.nationaldirectdebit.models.responses.*
import uk.gov.hmrc.nationaldirectdebit.services.DirectDebitService

import java.time.{LocalDate, LocalDateTime}
import scala.concurrent.Future

class DirectDebitServiceSpec extends SpecBase {

  val mockConnector: DirectDebitConnector = mock[DirectDebitConnector]
  val testService: DirectDebitService = new DirectDebitService(connector = mockConnector)
  val testDataCacheResponse: RDSDatacacheResponse = RDSDatacacheResponse(
    directDebitCount = 2,
    directDebitList = Seq(
      RDSDirectDebitDetails(
        ddiRefNumber       = "testRef",
        submissionDateTime = LocalDateTime.of(2025, 12, 12, 12, 12),
        bankSortCode       = "testCode",
        bankAccountNumber  = "testNumber",
        bankAccountName    = "testName",
        auDdisFlag         = true,
        numberOfPayPlans   = 1
      ),
      RDSDirectDebitDetails(
        ddiRefNumber       = "testRef",
        submissionDateTime = LocalDateTime.of(2025, 12, 12, 12, 12),
        bankSortCode       = "testCode",
        bankAccountNumber  = "testNumber",
        bankAccountName    = "testName",
        auDdisFlag         = true,
        numberOfPayPlans   = 1
      )
    )
  )

  val testDDPaymentPlansCacheResponse: RDSDDPaymentPlansResponse = RDSDDPaymentPlansResponse(
    bankSortCode      = "sort code",
    bankAccountNumber = "account number",
    bankAccountName   = "account name",
    auDdisFlag        = "dd",
    paymentPlanCount  = 2,
    paymentPlanList = Seq(
      RDSPaymentPlan(
        scheduledPaymentAmount = 100,
        planRefNumber          = "ref number 1",
        planType               = "01",
        paymentReference       = "payment ref 1",
        hodService             = "CESA",
        submissionDateTime     = LocalDateTime.of(2025, 12, 12, 12, 12)
      ),
      RDSPaymentPlan(
        scheduledPaymentAmount = 100,
        planRefNumber          = "ref number 1",
        planType               = "01",
        paymentReference       = "payment ref 1",
        hodService             = "CESA",
        submissionDateTime     = LocalDateTime.of(2025, 12, 12, 12, 12)
      )
    )
  )

  private val currentTime = LocalDateTime.MIN

  val testPaymentPlanResponse: RDSPaymentPlanResponse = RDSPaymentPlanResponse(
    directDebitDetails = DirectDebitDetail(bankSortCode = Some("sort code"),
                                           bankAccountNumber  = Some("account number"),
                                           bankAccountName    = None,
                                           auDdisFlag         = true,
                                           submissionDateTime = currentTime
                                          ),
    paymentPlanDetails = PaymentPlanDetail(
      hodService                = "CESA",
      planType                  = "01",
      paymentReference          = "payment reference",
      submissionDateTime        = currentTime,
      scheduledPaymentAmount    = Some(1000),
      scheduledPaymentStartDate = Some(currentTime.toLocalDate),
      initialPaymentStartDate   = Some(currentTime.toLocalDate),
      initialPaymentAmount      = Some(150),
      scheduledPaymentEndDate   = Some(currentTime.toLocalDate),
      scheduledPaymentFrequency = Some(1),
      suspensionStartDate       = Some(currentTime.toLocalDate),
      suspensionEndDate         = None,
      balancingPaymentAmount    = Some(600),
      balancingPaymentDate      = Some(currentTime.toLocalDate),
      totalLiability            = None,
      paymentPlanEditable       = false
    )
  )

  val duplicateCheckRequest: PaymentPlanDuplicateCheckRequest = PaymentPlanDuplicateCheckRequest(
    directDebitReference = "testRef",
    paymentPlanReference = "payment ref 123",
    planType             = "01",
    paymentService       = "CESA",
    paymentReference     = "payment ref",
    paymentAmount        = 120.00,
    totalLiability       = 780.00,
    paymentFrequency     = Some(1),
    paymentStartDate     = currentTime.toLocalDate
  )

  "DirectDebitService" - {
    "retrieveDirectDebits method" - {
      "must return the response from the connector" in {
        when(mockConnector.retrieveDirectDebits()(any())).thenReturn(Future.successful(testDataCacheResponse))
        val result = testService.retrieveDirectDebits().futureValue

        result mustBe testDataCacheResponse
      }
    }

    "AddWorkingDaysOffset method" - {
      "must return the response from the connector" in {
        when(mockConnector.getWorkingDaysOffset(any())(any())).thenReturn(Future.successful(EarliestPaymentDateResponse(LocalDate.of(2025, 12, 12))))
        val result =
          testService.getWorkingDaysOffset(WorkingDaysOffsetRequest(baseDate = LocalDate.of(2025, 12, 12), offsetWorkingDays = 10)).futureValue

        result mustBe EarliestPaymentDateResponse(LocalDate.of(2025, 12, 12))
      }
    }

    "generateDdiReference method" - {
      "must return the response from the connector" in {
        when(mockConnector.generateDdiReference(any())(any())).thenReturn(Future.successful(GenerateDdiRefResponse("12345")))
        val result = testService.generateDdiReference(GenerateDdiRefRequest("12345")).futureValue

        result mustBe GenerateDdiRefResponse("12345")
      }
    }

    "retrieveDirectDebitPaymentPlans method" - {
      "must return the response from the connector" in {
        when(mockConnector.retrieveDirectDebitPaymentPlans(any())(any())).thenReturn(Future.successful(testDDPaymentPlansCacheResponse))
        val result = testService.retrieveDirectDebitPaymentPlans("directDebitReference").futureValue

        result mustBe testDDPaymentPlansCacheResponse
      }
    }

    "retrievePaymentPlanDetails method" - {
      "must return the response from the connector" in {
        when(mockConnector.retrievePaymentPlanDetails(any(), any())(any())).thenReturn(Future.successful(testPaymentPlanResponse))
        val result = testService.retrievePaymentPlanDetails("test-dd-reference", "test-pp-reference").futureValue

        result mustBe testPaymentPlanResponse
      }
    }

    "lockPaymentPlan method" - {
      "must return the response from the connector" in {
        when(mockConnector.lockPaymentPlan(any(), any())(any()))
          .thenReturn(Future.successful(RDSPaymentPlanLock(lockSuccessful = true)))
        val result = testService.lockPaymentPlan("test-dd-reference", "test-pp-reference").futureValue

        result mustBe RDSPaymentPlanLock(lockSuccessful = true)
      }
    }

    "isDuplicatePaymentPlan method" - {
      "must return the response from the connector" in {
        when(mockConnector.isDuplicatePaymentPlan(any())(any())).thenReturn(Future.successful(DuplicateCheckResponse(true)))
        val result: DuplicateCheckResponse = testService.isDuplicatePaymentPlan(duplicateCheckRequest).futureValue

        result mustBe DuplicateCheckResponse(true)
      }
    }
  }
}
