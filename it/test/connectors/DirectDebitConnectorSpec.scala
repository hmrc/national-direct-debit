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

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, post, stubFor, urlPathMatching}
import itutil.ApplicationWithWiremock
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.must.Matchers
import org.scalatest.matchers.must.Matchers.mustBe
import play.api.http.Status.{CREATED, INTERNAL_SERVER_ERROR, OK}
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.nationaldirectdebit.connectors.DirectDebitConnector
import uk.gov.hmrc.nationaldirectdebit.models.requests.{CreateDirectDebitRequest, GenerateDdiRefRequest, WorkingDaysOffsetRequest}
import uk.gov.hmrc.nationaldirectdebit.models.responses.{EarliestPaymentDateResponse, GenerateDdiRefResponse, RDSDatacacheResponse, RDSDirectDebitDetails}

import java.time.{LocalDate, LocalDateTime}

class DirectDebitConnectorSpec extends ApplicationWithWiremock
  with Matchers
  with ScalaFutures
  with IntegrationPatience {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val connector: DirectDebitConnector = app.injector.instanceOf[DirectDebitConnector]

  val testEmptyDataCacheResponse: RDSDatacacheResponse = RDSDatacacheResponse(directDebitCount = 0, directDebitList = Seq.empty)
  val testDataCacheResponse: RDSDatacacheResponse = RDSDatacacheResponse(directDebitCount = 2,
    directDebitList = Seq(
      RDSDirectDebitDetails(ddiRefNumber = "testRef", submissionDateTime = LocalDateTime.of(2025, 12, 12, 12, 12), bankSortCode = "testCode", bankAccountNumber = "testNumber", bankAccountName = "testName", auDdisFlag = true, numberOfPayPlans = 1),
      RDSDirectDebitDetails(ddiRefNumber = "testRef", submissionDateTime = LocalDateTime.of(2025, 12, 12, 12, 12), bankSortCode = "testCode", bankAccountNumber = "testNumber", bankAccountName = "testName", auDdisFlag = true, numberOfPayPlans = 1)
    ))

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

        val result = connector.retrieveDirectDebits(2).futureValue

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

        val result = connector.retrieveDirectDebits(0).futureValue

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

        val result = intercept[Exception](connector.retrieveDirectDebits(2).futureValue)

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

        val result = intercept[Exception](connector.retrieveDirectDebits(2).futureValue)

        result.getMessage must include("The future returned an exception")
      }
    }

    "createDirectDebit" should {
      "successfully create a direct debit" in {
        stubFor(
          post(urlPathMatching("/rds-datacache-proxy/direct-debits"))
            .willReturn(
              aResponse()
                .withStatus(CREATED)
                .withBody("testReference")
            )
        )

        val requestBody = CreateDirectDebitRequest("testReference")
        val result = connector.createDirectDebit(requestBody).futureValue

        result mustBe "testReference"
      }

      "must fail when the result is parsed as an UpstreamErrorResponse" in {
        stubFor(
          post(urlPathMatching("/rds-datacache-proxy/direct-debits"))
            .willReturn(
              aResponse()
                .withStatus(INTERNAL_SERVER_ERROR)
                .withBody("test error")
            )
        )

        val requestBody = CreateDirectDebitRequest("testReference")
        val result = intercept[Exception](connector.createDirectDebit(requestBody).futureValue)

        result.getMessage must include("returned 500. Response body: 'test error'")
      }

      "must fail when the result is a failed future" in {
        stubFor(
          post(urlPathMatching("/national-direct-debit/direct-debits"))
            .willReturn(
              aResponse()
                .withStatus(0)
            )
        )

        val requestBody = CreateDirectDebitRequest("testReference")
        val result = intercept[Exception](connector.createDirectDebit(requestBody).futureValue)

        result.getMessage must include("The future returned an exception")
      }
    }

    "getEarliestPaymentDate" should {
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
          post(urlPathMatching("/national-direct-debit/direct-debits/future-working-days"))
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
          post(urlPathMatching("/rds-datacache-proxy/direct-debits/direct-debit-reference"))
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
          post(urlPathMatching("/rds-datacache-proxy/direct-debits/direct-debit-reference"))
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
          post(urlPathMatching("/rds-datacache-proxy/direct-debits/direct-debit-reference"))
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
  }
}
