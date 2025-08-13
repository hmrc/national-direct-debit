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

package uk.gov.hmrc.nationaldirectdebit.services

import com.google.inject.Inject
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.nationaldirectdebit.connectors.DirectDebitConnector
import uk.gov.hmrc.nationaldirectdebit.models.responses.{EarliestPaymentDateResponse, RDSDatacacheResponse}
import uk.gov.hmrc.nationaldirectdebit.models.requests.{CreateDirectDebitRequest, WorkingDaysOffsetRequest}

import scala.concurrent.Future

class DirectDebitService @Inject()(
                                    connector: DirectDebitConnector
                                  ) {

  def retrieveDirectDebits(limit: Int)(implicit hc: HeaderCarrier): Future[RDSDatacacheResponse] = {
    connector.retrieveDirectDebits(limit = limit)
  }

  def createDirectDebit(createDirectDebitRequest: CreateDirectDebitRequest)(implicit hc: HeaderCarrier): Future[String] = {
    connector.createDirectDebit(createDirectDebitRequest)
  }

  def getWorkingDaysOffset(getWorkingDaysOffsetRequest: WorkingDaysOffsetRequest)(implicit hc: HeaderCarrier): Future[EarliestPaymentDateResponse] = {
    connector.getWorkingDaysOffset(getWorkingDaysOffsetRequest)
  }

}