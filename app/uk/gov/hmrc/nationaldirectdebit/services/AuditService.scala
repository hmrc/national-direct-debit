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
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.nationaldirectdebit.models.audits.AuditEvent
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent
import uk.gov.hmrc.nationaldirectdebit.models.requests.chris.{DirectDebitSource, EnvelopeDetails, PaymentPlanType, PaymentsFrequency}
import uk.gov.hmrc.nationaldirectdebit.models.audits.*
import uk.gov.hmrc.http.NotFoundException

import scala.concurrent.{ExecutionContext, Future}

class AuditService @Inject (
  auditConnector: AuditConnector
)(implicit ec: ExecutionContext) {

  private val auditSource: String = "national-direct-debit"

  private def buildAuditEvent(envelopeDetails: EnvelopeDetails): AuditEvent = {

    val commonAuditFields: CommonAuditFields = CommonAuditFields(
      envelopeDetails.request.auditType.map(_.name).getOrElse("Other"),
      envelopeDetails.correlatingId,
      envelopeDetails.credId,
      envelopeDetails.request.ddiReferenceNo,
      envelopeDetails.submissionDateTime,
      DirectDebitSource.auditName(envelopeDetails.request.serviceType),
      PaymentPlanType.auditName(envelopeDetails.request.paymentPlanType),
      envelopeDetails.request.paymentReference
    )

    envelopeDetails.request.auditType match {
      case Some(NewDirectDebitAudit) =>
        val bankAccount = BankAccount(
          envelopeDetails.request.yourBankDetailsWithAuddisStatus.accountHolderName,
          envelopeDetails.request.yourBankDetailsWithAuddisStatus.accountNumber,
          envelopeDetails.request.yourBankDetailsWithAuddisStatus.sortCode
        )

        NewDirectDebitAuditEvent(
          commonAuditFields,
          envelopeDetails.request.bankAccountType.map(_.toString).getOrElse("NoAccountType"),
          bankAccount,
          envelopeDetails.request.yourBankDetailsWithAuddisStatus.auddisStatus, // Check the position
          Some("GBP"),
          Some(
            PaymentPlanDetails(
              paymentAmount = envelopeDetails.request.paymentAmount,
              paymentDate   = envelopeDetails.request.paymentDate.map(_.enteredDate),
              paymentFrequency = envelopeDetails.request match {
                case r
                    if r.serviceType == DirectDebitSource.TC &&
                      (r.paymentPlanType == PaymentPlanType.TaxCreditRepaymentPlan ||
                        r.paymentPlanType == PaymentPlanType.BudgetPaymentPlan) =>
                  PaymentsFrequency.auditName(Some(PaymentsFrequency.Monthly))

                case r =>
                  PaymentsFrequency.auditName(r.paymentFrequency)
              },
              regularPaymentAmount =
                envelopeDetails.request.regularPaymentAmount.orElse(envelopeDetails.request.calculation.flatMap(_.regularPaymentAmount)),
              planStartDate        = envelopeDetails.request.planStartDate.map(_.enteredDate),
              planEndDate          = envelopeDetails.request.planEndDate,
              totalAmountDue       = envelopeDetails.request.totalAmountDue,
              monthlyPaymentAmount = envelopeDetails.request.calculation.flatMap(_.monthlyPaymentAmount),
              finalPaymentAmount   = envelopeDetails.request.calculation.flatMap(_.finalPaymentAmount),
              finalPaymentDate     = envelopeDetails.request.calculation.flatMap(_.finalPaymentDate)
            )
          )
        )

      case Some(AddPaymentPlanAudit) =>
        AddPaymentPlanAuditEvent(
          commonAuditFields,
          Some("GBP"),
          Some(
            PaymentPlanDetails(
              paymentAmount = envelopeDetails.request.paymentAmount,
              paymentDate   = envelopeDetails.request.paymentDate.map(_.enteredDate),
              paymentFrequency = envelopeDetails.request match {
                case r
                    if r.serviceType == DirectDebitSource.TC &&
                      (r.paymentPlanType == PaymentPlanType.TaxCreditRepaymentPlan ||
                        r.paymentPlanType == PaymentPlanType.BudgetPaymentPlan) =>
                  PaymentsFrequency.auditName(Some(PaymentsFrequency.Monthly))

                case r =>
                  PaymentsFrequency.auditName(r.paymentFrequency)
              },
              regularPaymentAmount =
                envelopeDetails.request.regularPaymentAmount.orElse(envelopeDetails.request.calculation.flatMap(_.regularPaymentAmount)),
              planStartDate        = envelopeDetails.request.planStartDate.map(_.enteredDate),
              planEndDate          = envelopeDetails.request.planEndDate,
              totalAmountDue       = envelopeDetails.request.totalAmountDue,
              monthlyPaymentAmount = envelopeDetails.request.calculation.flatMap(_.monthlyPaymentAmount),
              finalPaymentAmount   = envelopeDetails.request.calculation.flatMap(_.finalPaymentAmount),
              finalPaymentDate     = envelopeDetails.request.calculation.flatMap(_.finalPaymentDate)
            )
          )
        )

      case Some(AmendPaymentPlanAudit) =>
        AmendPaymentPlanAuditEvent(
          commonAuditFields,
          Some("GBP"),
          Some(
            PaymentPlanDetails(
              paymentAmount = envelopeDetails.request match {
                case r if r.paymentPlanType == PaymentPlanType.SinglePayment =>
                  r.amendPaymentAmount
                case r =>
                  r.paymentAmount
              },
              paymentDate      = envelopeDetails.request.paymentDate.map(_.enteredDate),
              paymentFrequency = PaymentsFrequency.auditName(envelopeDetails.request.paymentFrequency),
              regularPaymentAmount = envelopeDetails.request match {
                case r if r.paymentPlanType == PaymentPlanType.BudgetPaymentPlan =>
                  r.amendPaymentAmount
                case r =>
                  r.regularPaymentAmount.orElse(r.calculation.flatMap(_.regularPaymentAmount))
              },
              planStartDate        = envelopeDetails.request.planStartDate.map(_.enteredDate),
              planEndDate          = envelopeDetails.request.planEndDate,
              totalAmountDue       = envelopeDetails.request.totalAmountDue,
              monthlyPaymentAmount = envelopeDetails.request.calculation.flatMap(_.monthlyPaymentAmount),
              finalPaymentAmount   = envelopeDetails.request.calculation.flatMap(_.finalPaymentAmount),
              finalPaymentDate     = envelopeDetails.request.calculation.flatMap(_.finalPaymentDate)
            )
          )
        )

      case Some(SuspendPaymentPlanAudit) =>
        SuspendPaymentPlanAuditEvent(
          commonAuditFields,
          envelopeDetails.request.suspensionPeriodRangeDate.map(_.startDate),
          envelopeDetails.request.suspensionPeriodRangeDate.map(_.endDate)
        )

      case Some(AmendPaymentPlanSuspensionAudit) =>
        AmendPaymentPlanSuspensionAuditEvent(
          commonAuditFields,
          envelopeDetails.request.suspensionPeriodRangeDate.map(_.startDate),
          envelopeDetails.request.suspensionPeriodRangeDate.map(_.endDate)
        )

      case Some(RemovePaymentPlanSuspensionAudit) =>
        RemovePaymentPlanSuspensionAuditEvent(
          commonAuditFields,
          envelopeDetails.request.removeSuspensionPlan
        )

      case Some(CancelPaymentPlanAudit) =>
        CancelPaymentPlanAuditEvent(
          commonAuditFields,
          envelopeDetails.request.cancelPlan
        )

      case _ =>
        throw new NotFoundException("Missing or not required audit")
    }
  }

  def sendEvent(envelopeDetails: EnvelopeDetails)(implicit hc: HeaderCarrier): Future[AuditResult] = {

    val extendedDataEvent = ExtendedDataEvent(
      auditSource = auditSource,
      auditType   = envelopeDetails.request.auditType.map(_.name).getOrElse("Other"),
      detail      = Json.toJson(buildAuditEvent(envelopeDetails))
    )

    auditConnector.sendExtendedEvent(extendedDataEvent)
  }

}
