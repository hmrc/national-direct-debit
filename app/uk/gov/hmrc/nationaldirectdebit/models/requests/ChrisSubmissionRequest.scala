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

package uk.gov.hmrc.nationaldirectdebit.models.requests

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.nationaldirectdebit.models.requests.chris.{BankAddress, DirectDebitSource, PaymentDateDetails, PaymentPlanCalculation, PaymentPlanType, PaymentsFrequency, PlanStartDateDetails, YearEndAndMonth, YourBankDetails, YourBankDetailsWithAuddisStatus}


case class ChrisSubmissionRequest(
                                   serviceType: DirectDebitSource,
                                   paymentPlanType: PaymentPlanType,
                                   paymentFrequency: Option[PaymentsFrequency],            // optional
                                   yourBankDetailsWithAuddisStatus: YourBankDetailsWithAuddisStatus,
                                   auddisStatus: Option[Boolean],
                                   planStartDate: Option[PlanStartDateDetails],                       // optional
                                   paymentDate: Option[PaymentDateDetails],                        // optional
                                   yearEndAndMonth: Option[YearEndAndMonth],              // optional
                                   bankDetails: YourBankDetails,
                                   bankDetailsAddress: BankAddress,
                                   ddiReferenceNo: String,
                                   bankName: String,
                                   calculation: Option[PaymentPlanCalculation]            // optional
                                 )

object ChrisSubmissionRequest {
  implicit val format: OFormat[ChrisSubmissionRequest] = Json.format[ChrisSubmissionRequest]
}
