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
import uk.gov.hmrc.nationaldirectdebit.models.responses.*
import uk.gov.hmrc.nationaldirectdebit.models.requests.*

import scala.concurrent.Future

class DirectDebitService @Inject() (
  connector: DirectDebitConnector
) {

  def retrieveDirectDebits()(implicit hc: HeaderCarrier): Future[RDSDatacacheResponse] = {
    connector.retrieveDirectDebits()
  }

  def getWorkingDaysOffset(getWorkingDaysOffsetRequest: WorkingDaysOffsetRequest)(implicit hc: HeaderCarrier): Future[EarliestPaymentDateResponse] = {
    connector.getWorkingDaysOffset(getWorkingDaysOffsetRequest)
  }

  def generateDdiReference(request: GenerateDdiRefRequest)(implicit hc: HeaderCarrier): Future[GenerateDdiRefResponse] = {
    connector.generateDdiReference(request)
  }

  def retrieveDirectDebitPaymentPlans(directDebitReference: String)(implicit hc: HeaderCarrier): Future[RDSDDPaymentPlansResponse] = {
    connector.retrieveDirectDebitPaymentPlans(directDebitReference)
  }

  def retrievePaymentPlanDetails(directDebitReference: String, paymentPlanReference: String)(implicit
    hc: HeaderCarrier
  ): Future[RDSPaymentPlanResponse] = {
    connector.retrievePaymentPlanDetails(directDebitReference, paymentPlanReference)
  }

  def lockPaymentPlan(directDebitReference: String, paymentPlanReference: String)(implicit
    hc: HeaderCarrier
  ): Future[RDSPaymentPlanLock] = {
    connector.lockPaymentPlan(directDebitReference, paymentPlanReference)
  }

  def isDuplicatePaymentPlan(request: PaymentPlanDuplicateCheckRequest)(implicit hc: HeaderCarrier): Future[DuplicateCheckResponse] = {
    connector.isDuplicatePaymentPlan(request)
  }

}
