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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.mockito.MockitoSugar.mock
import uk.gov.hmrc.http.{HeaderCarrier, NotFoundException}
import uk.gov.hmrc.nationaldirectdebit.models.audits.*
import uk.gov.hmrc.nationaldirectdebit.models.requests.*
import uk.gov.hmrc.nationaldirectdebit.models.requests.chris.*
import uk.gov.hmrc.nationaldirectdebit.models.requests.chris.PaymentPlanType.{BudgetPaymentPlan, TaxCreditRepaymentPlan}
import uk.gov.hmrc.nationaldirectdebit.services.AuditService
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent

import java.time.{LocalDate, LocalDateTime}
import scala.concurrent.Future

class AuditServiceSpec extends AsyncWordSpec with Matchers with ScalaFutures with MockitoSugar {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  private val mockAuditConnector = mock[AuditConnector]

  private val service = new AuditService(mockAuditConnector)

  // Plan and payment details
  private val planStartDateDetails = PlanStartDateDetails(
    enteredDate           = LocalDate.of(2025, 9, 1),
    earliestPlanStartDate = "2025-09-01"
  )

  private val planEndDate = LocalDate.of(2026, 5, 5)

  private val paymentDateDetails = PaymentDateDetails(
    enteredDate         = LocalDate.of(2025, 9, 15),
    earliestPaymentDate = "2025-09-01"
  )

  // Distinct requests for each service type
  private val tcRequest = ChrisSubmissionRequest(
    serviceType                = DirectDebitSource.TC,
    paymentPlanType            = PaymentPlanType.SinglePayment,
    paymentPlanReferenceNumber = None,
    paymentFrequency           = Some(PaymentsFrequency.Monthly),
    yourBankDetailsWithAuddisStatus = YourBankDetailsWithAuddisStatus(
      accountHolderName = "TC User",
      sortCode          = "11-22-33",
      accountNumber     = "12345678",
      auddisStatus      = true,
      accountVerified   = true
    ),
    planStartDate        = Some(planStartDateDetails),
    planEndDate          = None,
    paymentDate          = Some(paymentDateDetails),
    yearEndAndMonth      = None,
    ddiReferenceNo       = "TC-DDI-123",
    paymentReference     = "TCRef",
    totalAmountDue       = Some(123),
    paymentAmount        = Some(100),
    amendPaymentAmount   = None,
    regularPaymentAmount = Some(BigDecimal(25)),
    calculation = Some(PaymentPlanCalculation(Some(100), Some(100), Some(LocalDate.now()), Some(LocalDate.now()), Some(LocalDate.now()), Some(100))),
    suspensionPeriodRangeDate = None
  )

  private val saMonthlyRequest = ChrisSubmissionRequest(
    serviceType                = DirectDebitSource.SA,
    paymentPlanType            = PaymentPlanType.BudgetPaymentPlan,
    paymentPlanReferenceNumber = None,
    paymentFrequency           = Some(PaymentsFrequency.Monthly),
    yourBankDetailsWithAuddisStatus = YourBankDetailsWithAuddisStatus(
      accountHolderName = "SA Monthly User",
      sortCode          = "22-33-44",
      accountNumber     = "23456789",
      auddisStatus      = false,
      accountVerified   = false
    ),
    planStartDate        = Some(planStartDateDetails),
    planEndDate          = Some(planEndDate),
    paymentDate          = Some(paymentDateDetails),
    yearEndAndMonth      = None,
    ddiReferenceNo       = "SA-DDI-456",
    paymentReference     = "SARef",
    totalAmountDue       = Some(BigDecimal(200)),
    paymentAmount        = Some(BigDecimal(100)),
    amendPaymentAmount   = None,
    regularPaymentAmount = Some(BigDecimal(50)),
    calculation = Some(PaymentPlanCalculation(Some(100), Some(100), Some(LocalDate.now()), Some(LocalDate.now()), Some(LocalDate.now()), Some(100))),
    suspensionPeriodRangeDate = None
  )

  private val saWeeklyRequest = saMonthlyRequest.copy(
    paymentFrequency                = Some(PaymentsFrequency.Weekly),
    yourBankDetailsWithAuddisStatus = saMonthlyRequest.yourBankDetailsWithAuddisStatus.copy(accountHolderName = "SA Weekly User")
  )

  "sendEvent" should {

    "return Success when built up an audit event for NewDirectDebitAudit for tc SinglePaymentPlan" in {

      val envelopeDetails = EnvelopeDetails(
        request            = tcRequest.copy(auditType = Some(NewDirectDebitAudit)),
        credId             = "credId",
        knownFactData      = Seq(Map("key" -> "value")),
        keysData           = Seq(Map("key" -> "value")),
        correlatingId      = "correlatingId",
        receiptDate        = "receiptDate",
        submissionDateTime = "submissionDateTime",
        periodEnd          = "periodEnd",
        senderType         = "senderType",
        serviceType        = DirectDebitSource.TC,
        expectedHodService = None
      )
      when(mockAuditConnector.sendExtendedEvent(any[ExtendedDataEvent]())(any(), any()))
        .thenReturn(Future.successful(AuditResult.Success))

      val result = service.sendEvent(envelopeDetails)(hc).futureValue

      result mustBe AuditResult.Success
    }

    "return Success when built up an audit event for NewDirectDebitAudit for tc BudgetPaymentPlan" in {

      val envelopeDetails = EnvelopeDetails(
        request            = tcRequest.copy(auditType = Some(NewDirectDebitAudit), paymentPlanType = BudgetPaymentPlan, regularPaymentAmount = None),
        credId             = "credId",
        knownFactData      = Seq(Map("key" -> "value")),
        keysData           = Seq(Map("key" -> "value")),
        correlatingId      = "correlatingId",
        receiptDate        = "receiptDate",
        submissionDateTime = "submissionDateTime",
        periodEnd          = "periodEnd",
        senderType         = "senderType",
        serviceType        = DirectDebitSource.TC,
        expectedHodService = None
      )
      when(mockAuditConnector.sendExtendedEvent(any[ExtendedDataEvent]())(any(), any()))
        .thenReturn(Future.successful(AuditResult.Success))

      val result = service.sendEvent(envelopeDetails)(hc).futureValue

      result mustBe AuditResult.Success
    }

    "return Success when built up an audit event for NewDirectDebitAudit for tc TaxCreditRepaymentPlan" in {

      val envelopeDetails = EnvelopeDetails(
        request            = tcRequest.copy(auditType = Some(NewDirectDebitAudit), paymentPlanType = TaxCreditRepaymentPlan),
        credId             = "credId",
        knownFactData      = Seq(Map("key" -> "value")),
        keysData           = Seq(Map("key" -> "value")),
        correlatingId      = "correlatingId",
        receiptDate        = "receiptDate",
        submissionDateTime = "submissionDateTime",
        periodEnd          = "periodEnd",
        senderType         = "senderType",
        serviceType        = DirectDebitSource.TC,
        expectedHodService = None
      )
      when(mockAuditConnector.sendExtendedEvent(any[ExtendedDataEvent]())(any(), any()))
        .thenReturn(Future.successful(AuditResult.Success))

      val result = service.sendEvent(envelopeDetails)(hc).futureValue

      result mustBe AuditResult.Success
    }

    "return Success when built up an audit event for NewDirectDebitAudit monthly for sa BudgetPaymentPlan" in {

      val envelopeDetails = EnvelopeDetails(
        request            = saMonthlyRequest.copy(auditType = Some(NewDirectDebitAudit)),
        credId             = "credId",
        knownFactData      = Seq(Map("key" -> "value")),
        keysData           = Seq(Map("key" -> "value")),
        correlatingId      = "correlatingId",
        receiptDate        = "receiptDate",
        submissionDateTime = "submissionDateTime",
        periodEnd          = "periodEnd",
        senderType         = "senderType",
        serviceType        = DirectDebitSource.TC,
        expectedHodService = None
      )
      when(mockAuditConnector.sendExtendedEvent(any[ExtendedDataEvent]())(any(), any()))
        .thenReturn(Future.successful(AuditResult.Success))

      val result = service.sendEvent(envelopeDetails)(hc).futureValue

      result mustBe AuditResult.Success
    }

    "return Success when built up an audit event for NewDirectDebitAudit weekly for sa BudgetPaymentPlan" in {

      val envelopeDetails = EnvelopeDetails(
        request            = saWeeklyRequest.copy(auditType = Some(NewDirectDebitAudit)),
        credId             = "credId",
        knownFactData      = Seq(Map("key" -> "value")),
        keysData           = Seq(Map("key" -> "value")),
        correlatingId      = "correlatingId",
        receiptDate        = "receiptDate",
        submissionDateTime = "submissionDateTime",
        periodEnd          = "periodEnd",
        senderType         = "senderType",
        serviceType        = DirectDebitSource.TC,
        expectedHodService = None
      )
      when(mockAuditConnector.sendExtendedEvent(any[ExtendedDataEvent]())(any(), any()))
        .thenReturn(Future.successful(AuditResult.Success))

      val result = service.sendEvent(envelopeDetails)(hc).futureValue

      result mustBe AuditResult.Success
    }

    "return Success when built up an audit event for AddPaymentPlanAudit for tc SinglePaymentPlan" in {

      val envelopeDetails = EnvelopeDetails(
        request            = tcRequest.copy(auditType = Some(AddPaymentPlanAudit)),
        credId             = "credId",
        knownFactData      = Seq(Map("key" -> "value")),
        keysData           = Seq(Map("key" -> "value")),
        correlatingId      = "correlatingId",
        receiptDate        = "receiptDate",
        submissionDateTime = "submissionDateTime",
        periodEnd          = "periodEnd",
        senderType         = "senderType",
        serviceType        = DirectDebitSource.TC,
        expectedHodService = None
      )
      when(mockAuditConnector.sendExtendedEvent(any[ExtendedDataEvent]())(any(), any()))
        .thenReturn(Future.successful(AuditResult.Success))

      val result = service.sendEvent(envelopeDetails)(hc).futureValue

      result mustBe AuditResult.Success
    }

    "return Success when built up an audit event for AddPaymentPlanAudit for tc BudgetPaymentPlan" in {

      val envelopeDetails = EnvelopeDetails(
        request            = tcRequest.copy(auditType = Some(AddPaymentPlanAudit), paymentPlanType = BudgetPaymentPlan, regularPaymentAmount = None),
        credId             = "credId",
        knownFactData      = Seq(Map("key" -> "value")),
        keysData           = Seq(Map("key" -> "value")),
        correlatingId      = "correlatingId",
        receiptDate        = "receiptDate",
        submissionDateTime = "submissionDateTime",
        periodEnd          = "periodEnd",
        senderType         = "senderType",
        serviceType        = DirectDebitSource.TC,
        expectedHodService = None
      )
      when(mockAuditConnector.sendExtendedEvent(any[ExtendedDataEvent]())(any(), any()))
        .thenReturn(Future.successful(AuditResult.Success))

      val result = service.sendEvent(envelopeDetails)(hc).futureValue

      result mustBe AuditResult.Success
    }

    "return Success when built up an audit event for AddPaymentPlanAudit for tc TaxCreditRepaymentPlan" in {

      val envelopeDetails = EnvelopeDetails(
        request            = tcRequest.copy(auditType = Some(AddPaymentPlanAudit), paymentPlanType = TaxCreditRepaymentPlan),
        credId             = "credId",
        knownFactData      = Seq(Map("key" -> "value")),
        keysData           = Seq(Map("key" -> "value")),
        correlatingId      = "correlatingId",
        receiptDate        = "receiptDate",
        submissionDateTime = "submissionDateTime",
        periodEnd          = "periodEnd",
        senderType         = "senderType",
        serviceType        = DirectDebitSource.TC,
        expectedHodService = None
      )
      when(mockAuditConnector.sendExtendedEvent(any[ExtendedDataEvent]())(any(), any()))
        .thenReturn(Future.successful(AuditResult.Success))

      val result = service.sendEvent(envelopeDetails)(hc).futureValue

      result mustBe AuditResult.Success
    }

    "return Success when built up an audit event for AddPaymentPlanAudit monthly for sa BudgetPaymentPlan" in {

      val envelopeDetails = EnvelopeDetails(
        request            = saMonthlyRequest.copy(auditType = Some(AddPaymentPlanAudit)),
        credId             = "credId",
        knownFactData      = Seq(Map("key" -> "value")),
        keysData           = Seq(Map("key" -> "value")),
        correlatingId      = "correlatingId",
        receiptDate        = "receiptDate",
        submissionDateTime = "submissionDateTime",
        periodEnd          = "periodEnd",
        senderType         = "senderType",
        serviceType        = DirectDebitSource.TC,
        expectedHodService = None
      )
      when(mockAuditConnector.sendExtendedEvent(any[ExtendedDataEvent]())(any(), any()))
        .thenReturn(Future.successful(AuditResult.Success))

      val result = service.sendEvent(envelopeDetails)(hc).futureValue

      result mustBe AuditResult.Success
    }

    "return Success when built up an audit event for AddPaymentPlanAudit weekly for sa BudgetPaymentPlan" in {

      val envelopeDetails = EnvelopeDetails(
        request            = saWeeklyRequest.copy(auditType = Some(AddPaymentPlanAudit)),
        credId             = "credId",
        knownFactData      = Seq(Map("key" -> "value")),
        keysData           = Seq(Map("key" -> "value")),
        correlatingId      = "correlatingId",
        receiptDate        = "receiptDate",
        submissionDateTime = "submissionDateTime",
        periodEnd          = "periodEnd",
        senderType         = "senderType",
        serviceType        = DirectDebitSource.TC,
        expectedHodService = None
      )
      when(mockAuditConnector.sendExtendedEvent(any[ExtendedDataEvent]())(any(), any()))
        .thenReturn(Future.successful(AuditResult.Success))

      val result = service.sendEvent(envelopeDetails)(hc).futureValue

      result mustBe AuditResult.Success
    }

    "return Success when built up an audit event for AmendPaymentPlanAudit for tc SinglePaymentPlan" in {

      val envelopeDetails = EnvelopeDetails(
        request            = tcRequest.copy(auditType = Some(AmendPaymentPlanAudit)),
        credId             = "credId",
        knownFactData      = Seq(Map("key" -> "value")),
        keysData           = Seq(Map("key" -> "value")),
        correlatingId      = "correlatingId",
        receiptDate        = "receiptDate",
        submissionDateTime = "submissionDateTime",
        periodEnd          = "periodEnd",
        senderType         = "senderType",
        serviceType        = DirectDebitSource.TC,
        expectedHodService = None
      )
      when(mockAuditConnector.sendExtendedEvent(any[ExtendedDataEvent]())(any(), any()))
        .thenReturn(Future.successful(AuditResult.Success))

      val result = service.sendEvent(envelopeDetails)(hc).futureValue

      result mustBe AuditResult.Success
    }

    "return Success when built up an audit event for AmendPaymentPlanAudit for tc BudgetPaymentPlan" in {

      val envelopeDetails = EnvelopeDetails(
        request       = tcRequest.copy(auditType = Some(AmendPaymentPlanAudit), paymentPlanType = BudgetPaymentPlan, regularPaymentAmount = None),
        credId        = "credId",
        knownFactData = Seq(Map("key" -> "value")),
        keysData      = Seq(Map("key" -> "value")),
        correlatingId = "correlatingId",
        receiptDate   = "receiptDate",
        submissionDateTime = "submissionDateTime",
        periodEnd          = "periodEnd",
        senderType         = "senderType",
        serviceType        = DirectDebitSource.TC,
        expectedHodService = None
      )
      when(mockAuditConnector.sendExtendedEvent(any[ExtendedDataEvent]())(any(), any()))
        .thenReturn(Future.successful(AuditResult.Success))

      val result = service.sendEvent(envelopeDetails)(hc).futureValue

      result mustBe AuditResult.Success
    }

    "return Success when built up an audit event for AmendPaymentPlanAudit for tc TaxCreditRepaymentPlan" in {

      val envelopeDetails = EnvelopeDetails(
        request            = tcRequest.copy(auditType = Some(AmendPaymentPlanAudit), paymentPlanType = TaxCreditRepaymentPlan),
        credId             = "credId",
        knownFactData      = Seq(Map("key" -> "value")),
        keysData           = Seq(Map("key" -> "value")),
        correlatingId      = "correlatingId",
        receiptDate        = "receiptDate",
        submissionDateTime = "submissionDateTime",
        periodEnd          = "periodEnd",
        senderType         = "senderType",
        serviceType        = DirectDebitSource.TC,
        expectedHodService = None
      )
      when(mockAuditConnector.sendExtendedEvent(any[ExtendedDataEvent]())(any(), any()))
        .thenReturn(Future.successful(AuditResult.Success))

      val result = service.sendEvent(envelopeDetails)(hc).futureValue

      result mustBe AuditResult.Success
    }

    "return Success when built up an audit event for AmendPaymentPlanAudit monthly for sa BudgetPaymentPlan" in {

      val envelopeDetails = EnvelopeDetails(
        request            = saMonthlyRequest.copy(auditType = Some(AmendPaymentPlanAudit)),
        credId             = "credId",
        knownFactData      = Seq(Map("key" -> "value")),
        keysData           = Seq(Map("key" -> "value")),
        correlatingId      = "correlatingId",
        receiptDate        = "receiptDate",
        submissionDateTime = "submissionDateTime",
        periodEnd          = "periodEnd",
        senderType         = "senderType",
        serviceType        = DirectDebitSource.TC,
        expectedHodService = None
      )
      when(mockAuditConnector.sendExtendedEvent(any[ExtendedDataEvent]())(any(), any()))
        .thenReturn(Future.successful(AuditResult.Success))

      val result = service.sendEvent(envelopeDetails)(hc).futureValue

      result mustBe AuditResult.Success
    }

    "return Success when built up an audit event for AmendPaymentPlanAudit weekly for sa BudgetPaymentPlan" in {

      val envelopeDetails = EnvelopeDetails(
        request            = saWeeklyRequest.copy(auditType = Some(AmendPaymentPlanAudit)),
        credId             = "credId",
        knownFactData      = Seq(Map("key" -> "value")),
        keysData           = Seq(Map("key" -> "value")),
        correlatingId      = "correlatingId",
        receiptDate        = "receiptDate",
        submissionDateTime = "submissionDateTime",
        periodEnd          = "periodEnd",
        senderType         = "senderType",
        serviceType        = DirectDebitSource.TC,
        expectedHodService = None
      )
      when(mockAuditConnector.sendExtendedEvent(any[ExtendedDataEvent]())(any(), any()))
        .thenReturn(Future.successful(AuditResult.Success))

      val result = service.sendEvent(envelopeDetails)(hc).futureValue

      result mustBe AuditResult.Success
    }

    "return Success when built up an audit event for SuspendPaymentPlanAudit" in {

      val envelopeDetails = EnvelopeDetails(
        request            = tcRequest.copy(auditType = Some(SuspendPaymentPlanAudit)),
        credId             = "credId",
        knownFactData      = Seq(Map("key" -> "value")),
        keysData           = Seq(Map("key" -> "value")),
        correlatingId      = "correlatingId",
        receiptDate        = "receiptDate",
        submissionDateTime = "submissionDateTime",
        periodEnd          = "periodEnd",
        senderType         = "senderType",
        serviceType        = DirectDebitSource.TC,
        expectedHodService = None
      )
      when(mockAuditConnector.sendExtendedEvent(any[ExtendedDataEvent]())(any(), any()))
        .thenReturn(Future.successful(AuditResult.Success))

      val result = service.sendEvent(envelopeDetails)(hc).futureValue

      result mustBe AuditResult.Success
    }

    "return Success when built up an audit event for AmendPaymentPlanSuspensionAudit" in {

      val envelopeDetails = EnvelopeDetails(
        request            = tcRequest.copy(auditType = Some(AmendPaymentPlanSuspensionAudit)),
        credId             = "credId",
        knownFactData      = Seq(Map("key" -> "value")),
        keysData           = Seq(Map("key" -> "value")),
        correlatingId      = "correlatingId",
        receiptDate        = "receiptDate",
        submissionDateTime = "submissionDateTime",
        periodEnd          = "periodEnd",
        senderType         = "senderType",
        serviceType        = DirectDebitSource.TC,
        expectedHodService = None
      )
      when(mockAuditConnector.sendExtendedEvent(any[ExtendedDataEvent]())(any(), any()))
        .thenReturn(Future.successful(AuditResult.Success))

      val result = service.sendEvent(envelopeDetails)(hc).futureValue

      result mustBe AuditResult.Success
    }

    "return Success when built up an audit event for RemovePaymentPlanSuspensionAudit" in {

      val envelopeDetails = EnvelopeDetails(
        request            = tcRequest.copy(auditType = Some(RemovePaymentPlanSuspensionAudit)),
        credId             = "credId",
        knownFactData      = Seq(Map("key" -> "value")),
        keysData           = Seq(Map("key" -> "value")),
        correlatingId      = "correlatingId",
        receiptDate        = "receiptDate",
        submissionDateTime = "submissionDateTime",
        periodEnd          = "periodEnd",
        senderType         = "senderType",
        serviceType        = DirectDebitSource.TC,
        expectedHodService = None
      )
      when(mockAuditConnector.sendExtendedEvent(any[ExtendedDataEvent]())(any(), any()))
        .thenReturn(Future.successful(AuditResult.Success))

      val result = service.sendEvent(envelopeDetails)(hc).futureValue

      result mustBe AuditResult.Success
    }

    "return Success when built up an audit event for CancelPaymentPlanAudit" in {

      val envelopeDetails = EnvelopeDetails(
        request            = tcRequest.copy(auditType = Some(CancelPaymentPlanAudit)),
        credId             = "credId",
        knownFactData      = Seq(Map("key" -> "value")),
        keysData           = Seq(Map("key" -> "value")),
        correlatingId      = "correlatingId",
        receiptDate        = "receiptDate",
        submissionDateTime = "submissionDateTime",
        periodEnd          = "periodEnd",
        senderType         = "senderType",
        serviceType        = DirectDebitSource.TC,
        expectedHodService = None
      )
      when(mockAuditConnector.sendExtendedEvent(any[ExtendedDataEvent]())(any(), any()))
        .thenReturn(Future.successful(AuditResult.Success))

      val result = service.sendEvent(envelopeDetails)(hc).futureValue

      result mustBe AuditResult.Success
    }

    "throw NotFoundException when audit is missing or not required" in {

      val envelopeDetails = EnvelopeDetails(
        request            = tcRequest,
        credId             = "credId",
        knownFactData      = Seq(Map("key" -> "value")),
        keysData           = Seq(Map("key" -> "value")),
        correlatingId      = "correlatingId",
        receiptDate        = "receiptDate",
        submissionDateTime = "submissionDateTime",
        periodEnd          = "periodEnd",
        senderType         = "senderType",
        serviceType        = DirectDebitSource.TC,
        expectedHodService = None
      )

      val ex = intercept[NotFoundException] {
        service.sendEvent(envelopeDetails)(hc)
      }

      ex.getMessage mustBe "Missing or not required audit"
    }
  }
}
