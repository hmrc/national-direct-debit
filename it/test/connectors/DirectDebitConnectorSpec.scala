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

package connectors

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, post, put, stubFor, urlPathMatching}
import itutil.ApplicationWithWiremock
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.must.Matchers
import org.scalatest.matchers.must.Matchers.mustBe
import play.api.http.Status.{INTERNAL_SERVER_ERROR, OK}
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.nationaldirectdebit.connectors.DirectDebitConnector
import uk.gov.hmrc.nationaldirectdebit.models.requests.{GenerateDdiRefRequest, PaymentPlanDuplicateCheckRequest, WorkingDaysOffsetRequest}
import uk.gov.hmrc.nationaldirectdebit.models.responses.*

import java.time.{LocalDate, LocalDateTime}

class DirectDebitConnectorSpec extends ApplicationWithWiremock with Matchers with ScalaFutures with IntegrationPatience {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val connector: DirectDebitConnector = app.injector.instanceOf[DirectDebitConnector]

  val testEmptyDataCacheResponse: RDSDatacacheResponse = RDSDatacacheResponse(directDebitCount = 0, directDebitList = Seq.empty)
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
        planType               = "type 1",
        paymentReference       = "payment ref 1",
        hodService             = "service 1",
        submissionDateTime     = LocalDateTime.of(2025, 12, 12, 12, 12)
      ),
      RDSPaymentPlan(
        scheduledPaymentAmount = 100,
        planRefNumber          = "ref number 1",
        planType               = "type 1",
        paymentReference       = "payment ref 1",
        hodService             = "service 1",
        submissionDateTime     = LocalDateTime.of(2025, 12, 12, 12, 12)
      )
    )
  )

  val testEmptyDDPaymentPlansCacheResponse: RDSDDPaymentPlansResponse = RDSDDPaymentPlansResponse(
    bankSortCode      = "sort code",
    bankAccountNumber = "account number",
    bankAccountName   = "account name",
    auDdisFlag        = "dd",
    paymentPlanCount  = 2,
    paymentPlanList   = Seq.empty
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
      hodService                = "hod service",
      planType                  = "plan Type",
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

  "DirectDebtConnector" should {
    "retrieveDirectDebits" should {
      "successfully retrieve a direct debit" in {
        stubFor(
          get(urlPathMatching("/rds-datacache-proxy/direct-debits"))
            .willReturn(
              aResponse()
                .withStatus(OK)
                .withBody(Json.toJson(testDataCacheResponse).toString)
            )
        )

        val result = connector.retrieveDirectDebits().futureValue

        result mustBe testDataCacheResponse
      }

      "successfully retrieve empty direct debits" in {
        stubFor(
          get(urlPathMatching("/rds-datacache-proxy/direct-debits"))
            .willReturn(
              aResponse()
                .withStatus(OK)
                .withBody(Json.toJson(testEmptyDataCacheResponse).toString)
            )
        )

        val result = connector.retrieveDirectDebits().futureValue

        result mustBe testEmptyDataCacheResponse
      }

      "must fail when the result is parsed as an UpstreamErrorResponse" in {
        stubFor(
          get(urlPathMatching("/rds-datacache-proxy/direct-debits"))
            .willReturn(
              aResponse()
                .withStatus(INTERNAL_SERVER_ERROR)
                .withBody("test error")
            )
        )

        val result = intercept[Exception](connector.retrieveDirectDebits().futureValue)

        result.getMessage must include("returned 500. Response body: 'test error'")
      }

      "must fail when the result is a failed future" in {
        stubFor(
          get(urlPathMatching("/rds-datacache-proxy/direct-debits"))
            .willReturn(
              aResponse()
                .withStatus(0)
            )
        )

        val result = intercept[Exception](connector.retrieveDirectDebits().futureValue)

        result.getMessage must include("The future returned an exception")
      }
    }

    "AddFutureWorkingDays" should {
      "successfully retrieve a date" in {
        stubFor(
          post(urlPathMatching("/rds-datacache-proxy/direct-debits/future-working-days"))
            .willReturn(
              aResponse()
                .withStatus(OK)
                .withBody(s"""{"date":"2024-12-28"}""")
            )
        )

        val requestBody = WorkingDaysOffsetRequest(baseDate = LocalDate.of(2025, 12, 25), offsetWorkingDays = 3)
        val result = connector.getWorkingDaysOffset(requestBody).futureValue

        result mustBe EarliestPaymentDateResponse(LocalDate.of(2024, 12, 28))
      }

      "must fail when the result is parsed as an UpstreamErrorResponse" in {
        stubFor(
          post(urlPathMatching("/rds-datacache-proxy/direct-debits/future-working-days"))
            .willReturn(
              aResponse()
                .withStatus(INTERNAL_SERVER_ERROR)
                .withBody("test error")
            )
        )

        val requestBody = WorkingDaysOffsetRequest(baseDate = LocalDate.of(2025, 12, 25), offsetWorkingDays = 3)
        val result = intercept[Exception](connector.getWorkingDaysOffset(requestBody).futureValue)

        result.getMessage must include("returned 500. Response body: 'test error'")
      }

      "must fail when the result is a failed future" in {
        stubFor(
          post(urlPathMatching("/rds-datacache-proxy/direct-debits/future-working-days"))
            .willReturn(
              aResponse()
                .withStatus(0)
            )
        )

        val requestBody = WorkingDaysOffsetRequest(baseDate = LocalDate.of(2025, 12, 25), offsetWorkingDays = 3)
        val result = intercept[Exception](connector.getWorkingDaysOffset(requestBody).futureValue)

        result.getMessage must include("The future returned an exception")
      }
    }

    "retrieveDdiReference" should {
      "successfully retrieve reference number" in {
        stubFor(
          post(urlPathMatching("/rds-datacache-proxy/direct-debit-reference"))
            .willReturn(
              aResponse()
                .withStatus(OK)
                .withBody(s"""{"ddiRefNumber":"567890"}""")
            )
        )

        val requestBody = GenerateDdiRefRequest(paymentReference = "123456")
        val result = connector.generateDdiReference(requestBody).futureValue

        result mustBe GenerateDdiRefResponse("567890")
      }

      "must fail when the result is parsed as an UpstreamErrorResponse" in {
        stubFor(
          post(urlPathMatching("/rds-datacache-proxy/direct-debit-reference"))
            .willReturn(
              aResponse()
                .withStatus(INTERNAL_SERVER_ERROR)
                .withBody("test error")
            )
        )

        val requestBody = GenerateDdiRefRequest(paymentReference = "123456")
        val result = intercept[Exception](connector.generateDdiReference(requestBody).futureValue)

        result.getMessage must include("returned 500. Response body: 'test error'")
      }

      "must fail when the result is a failed future" in {
        stubFor(
          post(urlPathMatching("/rds-datacache-proxy/direct-debit-reference"))
            .willReturn(
              aResponse()
                .withStatus(0)
            )
        )

        val requestBody = GenerateDdiRefRequest(paymentReference = "123456")
        val result = intercept[Exception](connector.generateDdiReference(requestBody).futureValue)

        result.getMessage must include("The future returned an exception")
      }
    }

    "retrieveDirectDebitPaymentPlans" should {
      "successfully retrieve a direct debit payment plans" in {
        stubFor(
          get(urlPathMatching("/rds-datacache-proxy/direct-debits/testRef/payment-plans"))
            .willReturn(
              aResponse()
                .withStatus(OK)
                .withBody(Json.toJson(testDDPaymentPlansCacheResponse).toString)
            )
        )

        val result = connector.retrieveDirectDebitPaymentPlans("testRef").futureValue

        result mustBe testDDPaymentPlansCacheResponse
      }

      "successfully retrieve empty direct debits payment plans" in {
        stubFor(
          get(urlPathMatching("/rds-datacache-proxy/direct-debits/testRef/payment-plans"))
            .willReturn(
              aResponse()
                .withStatus(OK)
                .withBody(Json.toJson(testEmptyDDPaymentPlansCacheResponse).toString)
            )
        )

        val result = connector.retrieveDirectDebitPaymentPlans("testRef").futureValue

        result mustBe testEmptyDDPaymentPlansCacheResponse
      }

      "must fail when the result is parsed as an UpstreamErrorResponse" in {
        stubFor(
          get(urlPathMatching("/rds-datacache-proxy/direct-debits/testRef/payment-plans"))
            .willReturn(
              aResponse()
                .withStatus(INTERNAL_SERVER_ERROR)
                .withBody("test error")
            )
        )

        val result = intercept[Exception](connector.retrieveDirectDebitPaymentPlans("testRef").futureValue)

        result.getMessage must include("returned 500. Response body: 'test error'")
      }

      "must fail when the result is a failed future" in {
        stubFor(
          get(urlPathMatching("/rds-datacache-proxy/direct-debits/testRef/payment-plans"))
            .willReturn(
              aResponse()
                .withStatus(0)
            )
        )

        val result = intercept[Exception](connector.retrieveDirectDebitPaymentPlans("testRef").futureValue)

        result.getMessage must include("The future returned an exception")
      }
    }

    "retrievePaymentPlanDetails" should {
      "successfully retrieve a payment plan details" in {
        stubFor(
          get(urlPathMatching("/rds-datacache-proxy/direct-debits/test-dd-Ref/payment-plans/test-pp-reference"))
            .willReturn(
              aResponse()
                .withStatus(OK)
                .withBody(Json.toJson(testPaymentPlanResponse).toString)
            )
        )

        val result = connector.retrievePaymentPlanDetails("test-dd-Ref", "test-pp-reference").futureValue

        result mustBe testPaymentPlanResponse
      }

      "must fail when the result is parsed as an UpstreamErrorResponse" in {
        stubFor(
          get(urlPathMatching("/rds-datacache-proxy/direct-debits/test-dd-Ref/payment-plans/test-pp-reference"))
            .willReturn(
              aResponse()
                .withStatus(INTERNAL_SERVER_ERROR)
                .withBody("test error")
            )
        )

        val result = intercept[Exception](connector.retrievePaymentPlanDetails("test-dd-Ref", "test-pp-reference").futureValue)

        result.getMessage must include("returned 500. Response body: 'test error'")
      }

      "must fail when the result is a failed future" in {
        stubFor(
          get(urlPathMatching("/rds-datacache-proxy/direct-debits/test-dd-Ref/payment-plans/test-pp-reference"))
            .willReturn(
              aResponse()
                .withStatus(0)
            )
        )

        val result = intercept[Exception](connector.retrievePaymentPlanDetails("test-dd-Ref", "test-pp-reference").futureValue)

        result.getMessage must include("The future returned an exception")
      }
    }

    "lockPaymentPlan" should {
      "return lockSuccessful is true when payment plan locked" in {
        stubFor(
          put(urlPathMatching("/rds-datacache-proxy/direct-debits/test-dd-Ref/payment-plans/test-pp-reference/lock"))
            .willReturn(
              aResponse()
                .withStatus(OK)
                .withBody(Json.toJson(RDSPaymentPlanLock(lockSuccessful = true)).toString)
            )
        )

        val result = connector.lockPaymentPlan("test-dd-Ref", "test-pp-reference").futureValue

        result mustBe RDSPaymentPlanLock(lockSuccessful = true)
      }

      "return lockSuccessful is false when payment plan lock failed" in {
        stubFor(
          put(urlPathMatching("/rds-datacache-proxy/direct-debits/test-dd-Ref/payment-plans/test-pp-reference/lock"))
            .willReturn(
              aResponse()
                .withStatus(OK)
                .withBody(Json.toJson(RDSPaymentPlanLock(lockSuccessful = false)).toString)
            )
        )

        val result = connector.lockPaymentPlan("test-dd-Ref", "test-pp-reference").futureValue

        result mustBe RDSPaymentPlanLock(lockSuccessful = false)
      }

      "must fail when the result is parsed as an UpstreamErrorResponse" in {
        stubFor(
          put(urlPathMatching("/rds-datacache-proxy/direct-debits/test-dd-Ref/payment-plans/test-pp-reference/lock"))
            .willReturn(
              aResponse()
                .withStatus(INTERNAL_SERVER_ERROR)
                .withBody("test error")
            )
        )

        val result = intercept[Exception](connector.lockPaymentPlan("test-dd-Ref", "test-pp-reference").futureValue)

        result.getMessage must include("returned 500. Response body: 'test error'")
      }

      "must fail when the result is a failed future" in {
        stubFor(
          put(urlPathMatching("/rds-datacache-proxy/direct-debits/test-dd-Ref/payment-plans/test-pp-reference/lock"))
            .willReturn(
              aResponse()
                .withStatus(0)
            )
        )

        val result = intercept[Exception](connector.lockPaymentPlan("test-dd-Ref", "test-pp-reference").futureValue)

        result.getMessage must include("The future returned an exception")
      }
    }

    "isDuplicatePaymentPlan" should {
      "successfully retrieve true if is a duplicate payment plan" in {
        stubFor(
          post(urlPathMatching("/rds-datacache-proxy/direct-debits/testRef/duplicate-plan-check"))
            .willReturn(
              aResponse()
                .withStatus(OK)
                .withBody(Json.toJson(DuplicateCheckResponse(true)).toString)
            )
        )

        val result: DuplicateCheckResponse = connector.isDuplicatePaymentPlan(duplicateCheckRequest).futureValue

        result mustBe DuplicateCheckResponse(true)
      }

      "successfully retrieve false if it is not a duplicate payment plan" in {
        stubFor(
          post(urlPathMatching("/rds-datacache-proxy/direct-debits/testRef/duplicate-plan-check"))
            .willReturn(
              aResponse()
                .withStatus(OK)
                .withBody(Json.toJson(DuplicateCheckResponse(false)).toString)
            )
        )

        val result: DuplicateCheckResponse = connector.isDuplicatePaymentPlan(duplicateCheckRequest).futureValue

        result mustBe DuplicateCheckResponse(false)
      }

      "must fail when the result is parsed as an UpstreamErrorResponse" in {
        stubFor(
          post(urlPathMatching("/rds-datacache-proxy/direct-debits/testRef/duplicate-plan-check"))
            .willReturn(
              aResponse()
                .withStatus(INTERNAL_SERVER_ERROR)
                .withBody("test error")
            )
        )

        val result = intercept[Exception](connector.isDuplicatePaymentPlan(duplicateCheckRequest).futureValue)

        result.getMessage must include("returned 500. Response body: 'test error'")
      }

      "must fail when the result is a failed future" in {
        stubFor(
          get(urlPathMatching("/rds-datacache-proxy/direct-debits/testRef/duplicate-plan-check"))
            .willReturn(
              aResponse()
                .withStatus(0)
            )
        )

        val result = intercept[Exception](connector.isDuplicatePaymentPlan(duplicateCheckRequest).futureValue)

        result.getMessage must include("The future returned an exception")
      }
    }
  }
}
