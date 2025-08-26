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
import uk.gov.hmrc.nationaldirectdebit.models.requests.{CreateDirectDebitRequest, GenerateDdiRefRequest, WorkingDaysOffsetRequest}
import uk.gov.hmrc.nationaldirectdebit.models.responses.{EarliestPaymentDateResponse, GenerateDdiRefResponse, RDSDatacacheResponse, RDSDirectDebitDetails}
import uk.gov.hmrc.nationaldirectdebit.services.DirectDebitService

import java.time.{LocalDate, LocalDateTime}
import scala.concurrent.Future

class DirectDebitServiceSpec extends SpecBase {

  val mockConnector: DirectDebitConnector = mock[DirectDebitConnector]
  val testService: DirectDebitService = new DirectDebitService(connector = mockConnector)
  val testDataCacheResponse: RDSDatacacheResponse = RDSDatacacheResponse(directDebitCount = 2,
    directDebitList = Seq(
      RDSDirectDebitDetails(ddiRefNumber = "testRef", submissionDateTime = LocalDateTime.of(2025, 12, 12, 12, 12), bankSortCode = "testCode", bankAccountNumber = "testNumber", bankAccountName = "testName", auDdisFlag = true, numberOfPayPlans = 1),
      RDSDirectDebitDetails(ddiRefNumber = "testRef", submissionDateTime = LocalDateTime.of(2025, 12, 12, 12, 12), bankSortCode = "testCode", bankAccountNumber = "testNumber", bankAccountName = "testName", auDdisFlag = true, numberOfPayPlans = 1)
    ))

  "DirectDebitService" - {
    "retrieveDirectDebits method" - {
      "must return the response from the connector" in {
        when(mockConnector.retrieveDirectDebits(ArgumentMatchers.eq(2))(any())).thenReturn(Future.successful(testDataCacheResponse))
        val result = testService.retrieveDirectDebits(2).futureValue

        result mustBe testDataCacheResponse
      }
    }

    "createDirectDebit method" - {
      "must return the response from the connector" in {
        when(mockConnector.createDirectDebit(any())(any())).thenReturn(Future.successful("testRef"))
        val result = testService.createDirectDebit(CreateDirectDebitRequest(paymentReference = "testRef")).futureValue

        result mustBe "testRef"
      }
    }

    "getWorkingDaysOffset method" - {
      "must return the response from the connector" in {
        when(mockConnector.getWorkingDaysOffset(any())(any())).thenReturn(Future.successful(EarliestPaymentDateResponse(LocalDate.of(2025, 12, 12))))
        val result = testService.getWorkingDaysOffset(WorkingDaysOffsetRequest(baseDate = LocalDate.of(2025, 12, 12), offsetWorkingDays = 10)).futureValue

        result mustBe EarliestPaymentDateResponse(LocalDate.of(2025, 12, 12))
      }
    }
    
    "generateDdiReference method" - {
      "must return the response from the connector" in {
        val result = testService.generateDdiReference(GenerateDdiRefRequest("12345"))
        val expectedResult = GenerateDdiRefResponse("12345".hashCode.abs.toString)
        result mustBe expectedResult
      }
    }
  }

}
