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

package uk.gov.hmrc.nationaldirectdebit.connectors

import com.google.inject.Inject
import play.api.libs.json.Json
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpReadsInstances, StringContextOps}
import uk.gov.hmrc.nationaldirectdebit.models.responses.{EarliestPaymentDateResponse, GenerateDdiRefResponse, RDSDDPaymentPlansResponse, RDSDatacacheResponse}
import uk.gov.hmrc.nationaldirectdebit.models.requests.{GenerateDdiRefRequest, WorkingDaysOffsetRequest}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue

import scala.concurrent.{ExecutionContext, Future}

class DirectDebitConnector @Inject()(
                                      http: HttpClientV2,
                                      config: ServicesConfig
                                    )(implicit ec: ExecutionContext) extends HttpReadsInstances {

  private val rdsDatacacheProxyBaseUrl: String = config.baseUrl("rds-datacache-proxy") + "/rds-datacache-proxy"

  def retrieveDirectDebits()(implicit hc: HeaderCarrier): Future[RDSDatacacheResponse] = {
    http.get(url"$rdsDatacacheProxyBaseUrl/direct-debits")(hc)
      .execute[RDSDatacacheResponse]
  }

  def getWorkingDaysOffset(getWorkingDaysOffsetRequest: WorkingDaysOffsetRequest)(implicit hc: HeaderCarrier): Future[EarliestPaymentDateResponse] = {
    http.post(url"$rdsDatacacheProxyBaseUrl/direct-debits/future-working-days")(hc)
      .withBody(Json.toJson(getWorkingDaysOffsetRequest))
      .execute[EarliestPaymentDateResponse]
  }

  def generateDdiReference(getDdiRefRequest: GenerateDdiRefRequest)(implicit hc: HeaderCarrier): Future[GenerateDdiRefResponse] = {
    http.post(url"$rdsDatacacheProxyBaseUrl/direct-debit-reference")(hc)
      .withBody(Json.toJson(getDdiRefRequest))
      .execute[GenerateDdiRefResponse]
  }

  def retrieveDirectDebitPaymentPlans(paymentReference: String)(implicit hc: HeaderCarrier): Future[RDSDDPaymentPlansResponse] = {
    http.get(url"$rdsDatacacheProxyBaseUrl/direct-debit-reference?paymentReference=$paymentReference")(hc)
      .execute[RDSDDPaymentPlansResponse]
  }
}
