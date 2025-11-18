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
import play.api.test.FakeRequest
import uk.gov.hmrc.auth.core.{AuthConnector, Enrolment, EnrolmentIdentifier, Enrolments}
import uk.gov.hmrc.http.{HeaderCarrier, SessionId}
import uk.gov.hmrc.nationaldirectdebit.connectors.ChrisConnector
import uk.gov.hmrc.nationaldirectdebit.models.SuspensionPeriodRange
import uk.gov.hmrc.nationaldirectdebit.models.requests.*
import uk.gov.hmrc.nationaldirectdebit.models.requests.chris.*
import uk.gov.hmrc.nationaldirectdebit.services.{AuditService, ChrisService}
import uk.gov.hmrc.play.audit.http.connector.AuditResult.*

import java.time.LocalDate
import scala.concurrent.Future
import scala.xml.Elem

class ChrisServiceSpec extends AsyncWordSpec with Matchers with ScalaFutures with MockitoSugar {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  private val mockConnector = mock[ChrisConnector]
  private val mockAuthConnector = mock[AuthConnector]
  private val mockAuditService = mock[AuditService]

  private val service = new ChrisService(mockConnector, mockAuthConnector, mockAuditService)

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
    planStartDate             = Some(planStartDateDetails),
    planEndDate               = None,
    paymentDate               = Some(paymentDateDetails),
    yearEndAndMonth           = None,
    ddiReferenceNo            = "TC-DDI-123",
    paymentReference          = "TCRef",
    totalAmountDue            = None,
    paymentAmount             = None,
    amendPaymentAmount        = None,
    regularPaymentAmount      = Some(BigDecimal(25)),
    calculation               = None,
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
    planStartDate             = Some(planStartDateDetails),
    planEndDate               = Some(planEndDate),
    paymentDate               = Some(paymentDateDetails),
    yearEndAndMonth           = None,
    ddiReferenceNo            = "SA-DDI-456",
    paymentReference          = "SARef",
    totalAmountDue            = Some(BigDecimal(200)),
    paymentAmount             = Some(BigDecimal(100)),
    amendPaymentAmount        = None,
    regularPaymentAmount      = Some(BigDecimal(50)),
    calculation               = None,
    suspensionPeriodRangeDate = None
  )

  private val saWeeklyRequest = saMonthlyRequest.copy(
    paymentFrequency                = Some(PaymentsFrequency.Weekly),
    yourBankDetailsWithAuddisStatus = saMonthlyRequest.yourBankDetailsWithAuddisStatus.copy(accountHolderName = "SA Weekly User")
  )

  private val saAmendRequest = saMonthlyRequest.copy(
    amendPlan          = true,
    amendPaymentAmount = Some(BigDecimal(100)),
    totalAmountDue     = null
  )

  private val saBudgetingSuspendRequest = saMonthlyRequest.copy(
    suspendPlan = true,
    suspensionPeriodRangeDate = Some(
      SuspensionPeriodRange(
        startDate = LocalDate.of(2025, 5, 1),
        endDate   = LocalDate.of(2025, 6, 1)
      )
    ),
    totalAmountDue = None
  )

  private val saBudgetingRemoveSuspendRequest = saMonthlyRequest.copy(
    removeSuspensionPlan      = true,
    suspensionPeriodRangeDate = None,
    totalAmountDue            = None
  )

  private val saBudgetingSuspendRequestWeekly = saWeeklyRequest.copy(
    suspendPlan = true,
    suspensionPeriodRangeDate = Some(
      SuspensionPeriodRange(
        startDate = LocalDate.of(2025, 5, 1),
        endDate   = LocalDate.of(2025, 6, 1)
      )
    ),
    totalAmountDue = None
  )

  private val saCancelequest = saMonthlyRequest.copy(
    cancelPlan = true
  )

  private val amendSingleRequest = ChrisSubmissionRequest(
    serviceType                = DirectDebitSource.SA,
    paymentPlanType            = PaymentPlanType.SinglePayment,
    paymentPlanReferenceNumber = None,
    paymentFrequency           = Some(PaymentsFrequency.Monthly),
    yourBankDetailsWithAuddisStatus = YourBankDetailsWithAuddisStatus(
      accountHolderName = "CT User",
      sortCode          = "33-44-55",
      accountNumber     = "34567890",
      auddisStatus      = true,
      accountVerified   = true
    ),
    planStartDate             = Some(planStartDateDetails),
    planEndDate               = None,
    paymentDate               = Some(paymentDateDetails),
    yearEndAndMonth           = None,
    ddiReferenceNo            = "CT-DDI-789",
    paymentReference          = "CTRef",
    totalAmountDue            = Some(BigDecimal(300)),
    paymentAmount             = None,
    regularPaymentAmount      = None,
    amendPaymentAmount        = Some(BigDecimal(75)),
    calculation               = None,
    amendPlan                 = true,
    suspensionPeriodRangeDate = None
  )

  private val cancelSingleRequest = ChrisSubmissionRequest(
    serviceType                = DirectDebitSource.SA,
    paymentPlanType            = PaymentPlanType.SinglePayment,
    paymentPlanReferenceNumber = None,
    paymentFrequency           = Some(PaymentsFrequency.Monthly),
    yourBankDetailsWithAuddisStatus = YourBankDetailsWithAuddisStatus(
      accountHolderName = "CT User",
      sortCode          = "33-44-55",
      accountNumber     = "34567890",
      auddisStatus      = true,
      accountVerified   = true
    ),
    planStartDate             = Some(planStartDateDetails),
    planEndDate               = None,
    paymentDate               = Some(paymentDateDetails),
    yearEndAndMonth           = None,
    ddiReferenceNo            = "CT-DDI-789",
    paymentReference          = "CTRef",
    totalAmountDue            = Some(BigDecimal(300)),
    paymentAmount             = None,
    regularPaymentAmount      = None,
    amendPaymentAmount        = None,
    calculation               = None,
    cancelPlan                = true,
    suspensionPeriodRangeDate = None
  )

  private val ctRequest = ChrisSubmissionRequest(
    serviceType                = DirectDebitSource.CT,
    paymentPlanType            = PaymentPlanType.SinglePayment,
    paymentPlanReferenceNumber = None,
    paymentFrequency           = Some(PaymentsFrequency.Monthly),
    yourBankDetailsWithAuddisStatus = YourBankDetailsWithAuddisStatus(
      accountHolderName = "CT User",
      sortCode          = "33-44-55",
      accountNumber     = "34567890",
      auddisStatus      = true,
      accountVerified   = true
    ),
    planStartDate             = Some(planStartDateDetails),
    planEndDate               = None,
    paymentDate               = Some(paymentDateDetails),
    yearEndAndMonth           = None,
    ddiReferenceNo            = "CT-DDI-789",
    paymentReference          = "CTRef",
    totalAmountDue            = Some(BigDecimal(300)),
    paymentAmount             = Some(BigDecimal(150)),
    regularPaymentAmount      = Some(BigDecimal(75)),
    amendPaymentAmount        = None,
    calculation               = None,
    suspensionPeriodRangeDate = None
  )

  private val mgdRequest = ChrisSubmissionRequest(
    serviceType                = DirectDebitSource.MGD,
    paymentPlanType            = PaymentPlanType.VariablePaymentPlan,
    paymentPlanReferenceNumber = None,
    paymentFrequency           = None,
    yourBankDetailsWithAuddisStatus = YourBankDetailsWithAuddisStatus(
      accountHolderName = "MGD User",
      sortCode          = "44-55-66",
      accountNumber     = "45678901",
      auddisStatus      = false,
      accountVerified   = false
    ),
    planStartDate             = Some(planStartDateDetails),
    planEndDate               = None,
    paymentDate               = Some(paymentDateDetails),
    yearEndAndMonth           = None,
    ddiReferenceNo            = "MGD-DDI-101",
    paymentReference          = "MGDRef",
    totalAmountDue            = Some(BigDecimal(400)),
    paymentAmount             = Some(BigDecimal(200)),
    regularPaymentAmount      = Some(BigDecimal(100)),
    amendPaymentAmount        = None,
    calculation               = None,
    suspensionPeriodRangeDate = None
  )

  private val mgdCancelRequest = ChrisSubmissionRequest(
    serviceType                = DirectDebitSource.MGD,
    paymentPlanType            = PaymentPlanType.VariablePaymentPlan,
    paymentPlanReferenceNumber = None,
    paymentFrequency           = None,
    yourBankDetailsWithAuddisStatus = YourBankDetailsWithAuddisStatus(
      accountHolderName = "MGD User",
      sortCode          = "44-55-66",
      accountNumber     = "45678901",
      auddisStatus      = false,
      accountVerified   = false
    ),
    planStartDate             = Some(planStartDateDetails),
    planEndDate               = None,
    paymentDate               = Some(paymentDateDetails),
    yearEndAndMonth           = None,
    ddiReferenceNo            = "MGD-DDI-101",
    paymentReference          = "MGDRef",
    totalAmountDue            = Some(BigDecimal(400)),
    paymentAmount             = Some(BigDecimal(200)),
    regularPaymentAmount      = Some(BigDecimal(100)),
    amendPaymentAmount        = None,
    calculation               = None,
    cancelPlan                = true,
    suspensionPeriodRangeDate = None
  )

  private val vatRequest = ChrisSubmissionRequest(
    serviceType                = DirectDebitSource.MGD,
    paymentPlanType            = PaymentPlanType.VariablePaymentPlan,
    paymentPlanReferenceNumber = None,
    paymentFrequency           = None,
    yourBankDetailsWithAuddisStatus = YourBankDetailsWithAuddisStatus(
      accountHolderName = "MGD User",
      sortCode          = "44-55-66",
      accountNumber     = "45678901",
      auddisStatus      = false,
      accountVerified   = false
    ),
    planStartDate             = Some(planStartDateDetails),
    planEndDate               = None,
    paymentDate               = Some(paymentDateDetails),
    yearEndAndMonth           = None,
    ddiReferenceNo            = "MGD-DDI-101",
    paymentReference          = "MGDRef",
    totalAmountDue            = Some(BigDecimal(400)),
    paymentAmount             = Some(BigDecimal(200)),
    regularPaymentAmount      = Some(BigDecimal(100)),
    amendPaymentAmount        = None,
    calculation               = None,
    suspensionPeriodRangeDate = None
  )

  private val payeRequest = ChrisSubmissionRequest(
    serviceType                = DirectDebitSource.MGD,
    paymentPlanType            = PaymentPlanType.VariablePaymentPlan,
    paymentPlanReferenceNumber = None,
    paymentFrequency           = None,
    yourBankDetailsWithAuddisStatus = YourBankDetailsWithAuddisStatus(
      accountHolderName = "MGD User",
      sortCode          = "44-55-66",
      accountNumber     = "45678901",
      auddisStatus      = false,
      accountVerified   = false
    ),
    planStartDate             = Some(planStartDateDetails),
    planEndDate               = None,
    paymentDate               = Some(paymentDateDetails),
    yearEndAndMonth           = None,
    ddiReferenceNo            = "MGD-DDI-101",
    paymentReference          = "MGDRef",
    totalAmountDue            = Some(BigDecimal(400)),
    paymentAmount             = Some(BigDecimal(200)),
    regularPaymentAmount      = Some(BigDecimal(100)),
    amendPaymentAmount        = None,
    calculation               = None,
    suspensionPeriodRangeDate = None
  )

  val fakeAuthRequest = AuthenticatedRequest(
    request       = FakeRequest(),
    internalId    = "internalId-123",
    sessionId     = SessionId("session-123"),
    credId        = "credId123",
    affinityGroup = "Organisation",
    nino          = Some("AB123456C")
  )

  "ChrisService.submitToChris" should {

    "return confirmation when submission succeeds for TC" in {
      val enrolments = Enrolments(
        Set(
          Enrolment(
            key               = "NTC",
            identifiers       = Seq(EnrolmentIdentifier("Nino", "AB1234567A")),
            state             = "Activated",
            delegatedAuthRule = None
          )
        )
      )
      when(mockAuthConnector.authorise(any(), any())(any(), any())).thenReturn(Future.successful(enrolments))
      when(mockConnector.submitEnvelope(any[Elem])).thenReturn(Future.successful("<Confirmation>TC Message received</Confirmation>"))
      when(mockAuditService.sendEvent(any())(any())).thenReturn(Future.successful(Success))

      service.submitToChris(tcRequest, "credId123", "Organisation", fakeAuthRequest).map { result =>
        result must include("TC Message received")
      }
    }

    "return confirmation when submission succeeds for SA (monthly)" in {
      val enrolments = Enrolments(
        Set(
          Enrolment(
            key               = "IR-SA",
            identifiers       = Seq(EnrolmentIdentifier("TaxId", "1234567890")),
            state             = "Activated",
            delegatedAuthRule = None
          )
        )
      )
      when(mockAuthConnector.authorise(any(), any())(any(), any())).thenReturn(Future.successful(enrolments))
      when(mockConnector.submitEnvelope(any[Elem])).thenReturn(Future.successful("<Confirmation>SA Monthly Message received</Confirmation>"))
      when(mockAuditService.sendEvent(any())(any())).thenReturn(Future.successful(Success))

      service.submitToChris(saMonthlyRequest, "credId123", "Organisation", fakeAuthRequest).map { result =>
        result must include("SA Monthly Message received")
      }
    }

    "return confirmation when submission succeeds for SA Amend (monthly)" in {
      val enrolments = Enrolments(
        Set(
          Enrolment(
            key               = "IR-SA",
            identifiers       = Seq(EnrolmentIdentifier("TaxId", "1234567890")),
            state             = "Activated",
            delegatedAuthRule = None
          )
        )
      )
      when(mockAuthConnector.authorise(any(), any())(any(), any())).thenReturn(Future.successful(enrolments))
      when(mockConnector.submitEnvelope(any[Elem])).thenReturn(Future.successful("<Confirmation>SA amend Monthly Message received</Confirmation>"))
      when(mockAuditService.sendEvent(any())(any())).thenReturn(Future.successful(Success))

      service.submitToChris(saAmendRequest, "credId123", "Agent", fakeAuthRequest).map { result =>
        result must include("SA amend Monthly Message received")
      }
    }

    "return confirmation when submission succeeds for SA cancel (monthly)" in {
      val enrolments = Enrolments(
        Set(
          Enrolment(
            key               = "IR-SA",
            identifiers       = Seq(EnrolmentIdentifier("TaxId", "1234567890")),
            state             = "Activated",
            delegatedAuthRule = None
          )
        )
      )
      when(mockAuthConnector.authorise(any(), any())(any(), any())).thenReturn(Future.successful(enrolments))
      when(mockConnector.submitEnvelope(any[Elem])).thenReturn(Future.successful("<Confirmation>SA Cancel Monthly Message received</Confirmation>"))
      when(mockAuditService.sendEvent(any())(any())).thenReturn(Future.successful(Success))

      service.submitToChris(saCancelequest, "credId123", "Organisation", fakeAuthRequest).map { result =>
        result must include("SA Cancel Monthly Message received")
      }
    }

    "return confirmation when submission succeeds for SA suspend (monthly)" in {
      val enrolments = Enrolments(
        Set(
          Enrolment(
            key               = "IR-SA",
            identifiers       = Seq(EnrolmentIdentifier("TaxId", "1234567890")),
            state             = "Activated",
            delegatedAuthRule = None
          )
        )
      )

      when(mockAuthConnector.authorise(any(), any())(any(), any()))
        .thenReturn(Future.successful(enrolments))

      when(mockConnector.submitEnvelope(any[Elem]))
        .thenReturn(Future.successful("<Confirmation>SA Suspend Monthly Message received</Confirmation>"))

      when(mockAuditService.sendEvent(any())(any())).thenReturn(Future.successful(Success))

      service.submitToChris(saBudgetingSuspendRequest, "credId123", "Agent", fakeAuthRequest).map { result =>
        result must include("SA Suspend Monthly Message received")
      }
    }

    "return confirmation when submission succeeds for SA suspend (Weekly)" in {
      val enrolments = Enrolments(
        Set(
          Enrolment(
            key               = "IR-SA",
            identifiers       = Seq(EnrolmentIdentifier("TaxId", "1234567890")),
            state             = "Activated",
            delegatedAuthRule = None
          )
        )
      )

      when(mockAuthConnector.authorise(any(), any())(any(), any()))
        .thenReturn(Future.successful(enrolments))

      when(mockConnector.submitEnvelope(any[Elem]))
        .thenReturn(Future.successful("<Confirmation>SA Suspend Weekly Message received</Confirmation>"))

      when(mockAuditService.sendEvent(any())(any())).thenReturn(Future.successful(Success))

      service.submitToChris(saBudgetingSuspendRequestWeekly, "credId123", "Agent", fakeAuthRequest).map { result =>
        result must include("SA Suspend Weekly Message received")
      }
    }

    "return confirmation when submission succeeds for SA remove suspension (Weekly)" in {
      val enrolments = Enrolments(
        Set(
          Enrolment(
            key               = "IR-SA",
            identifiers       = Seq(EnrolmentIdentifier("TaxId", "1234567890")),
            state             = "Activated",
            delegatedAuthRule = None
          )
        )
      )

      when(mockAuthConnector.authorise(any(), any())(any(), any()))
        .thenReturn(Future.successful(enrolments))

      when(mockConnector.submitEnvelope(any[Elem]))
        .thenReturn(Future.successful("<Confirmation>SA Remove Suspension Weekly Message received</Confirmation>"))

      when(mockAuditService.sendEvent(any())(any())).thenReturn(Future.successful(Success))

      service.submitToChris(saBudgetingRemoveSuspendRequest, "credId123", "Agent", fakeAuthRequest).map { result =>
        result must include("SA Remove Suspension Weekly Message received")
      }
    }

    "return confirmation when submission succeeds for SA (weekly)" in {
      val enrolments = Enrolments(
        Set(
          Enrolment(
            key               = "CESA",
            identifiers       = Seq(EnrolmentIdentifier("UTR", "1234567890")),
            state             = "Activated",
            delegatedAuthRule = None
          )
        )
      )
      when(mockAuthConnector.authorise(any(), any())(any(), any())).thenReturn(Future.successful(enrolments))
      when(mockConnector.submitEnvelope(any[Elem])).thenReturn(Future.successful("<Confirmation>SA Weekly Message received</Confirmation>"))
      when(mockAuditService.sendEvent(any())(any())).thenReturn(Future.successful(Success))

      service.submitToChris(saWeeklyRequest, "credId123", "Organisation", fakeAuthRequest).map { result =>
        result must include("SA Weekly Message received")
      }
    }

    "return confirmation when submission succeeds for OTHER_LIABILITY" in {
      val enrolments = Enrolments(
        Set(
          Enrolment(
            key               = "SAFE",
            identifiers       = Seq(EnrolmentIdentifier("UTR", "1234567890")),
            state             = "Activated",
            delegatedAuthRule = None
          )
        )
      )
      when(mockAuthConnector.authorise(any(), any())(any(), any())).thenReturn(Future.successful(enrolments))
      when(mockConnector.submitEnvelope(any[Elem])).thenReturn(Future.successful("<Confirmation>OTHER_LIABILITY Message received</Confirmation>"))
      when(mockAuditService.sendEvent(any())(any())).thenReturn(Future.successful(Success))

      service.submitToChris(ctRequest, "credId123", "Individual", fakeAuthRequest).map { result =>
        result must include("OTHER_LIABILITY Message received")
      }
    }

    "return confirmation when submission succeeds for CT" in {
      val enrolments = Enrolments(
        Set(
          Enrolment(
            key               = "COTA",
            identifiers       = Seq(EnrolmentIdentifier("UTR", "1234567890")),
            state             = "Activated",
            delegatedAuthRule = None
          )
        )
      )
      when(mockAuthConnector.authorise(any(), any())(any(), any())).thenReturn(Future.successful(enrolments))
      when(mockConnector.submitEnvelope(any[Elem])).thenReturn(Future.successful("<Confirmation>CT Message received</Confirmation>"))
      when(mockAuditService.sendEvent(any())(any())).thenReturn(Future.successful(Success))

      service.submitToChris(ctRequest, "credId123", "Agent", fakeAuthRequest).map { result =>
        result must include("CT Message received")
      }
    }

    "return confirmation when submission succeeds for Amend Single" in {
      val enrolments = Enrolments(
        Set(
          Enrolment(
            key               = "COTA",
            identifiers       = Seq(EnrolmentIdentifier("UTR", "1234567890")),
            state             = "Activated",
            delegatedAuthRule = None
          )
        )
      )
      when(mockAuthConnector.authorise(any(), any())(any(), any())).thenReturn(Future.successful(enrolments))
      when(mockConnector.submitEnvelope(any[Elem])).thenReturn(Future.successful("<Confirmation>CT Message received</Confirmation>"))
      when(mockAuditService.sendEvent(any())(any())).thenReturn(Future.successful(Success))

      service.submitToChris(amendSingleRequest, "credId123", "Agent", fakeAuthRequest).map { result =>
        result must include("CT Message received")
      }
    }

    "return confirmation when submission succeeds for cancel single plan" in {
      val enrolments = Enrolments(
        Set(
          Enrolment(
            key               = "COTA",
            identifiers       = Seq(EnrolmentIdentifier("UTR", "1234567890")),
            state             = "Activated",
            delegatedAuthRule = None
          )
        )
      )
      when(mockAuthConnector.authorise(any(), any())(any(), any())).thenReturn(Future.successful(enrolments))
      when(mockConnector.submitEnvelope(any[Elem])).thenReturn(Future.successful("<Confirmation>Cancel Message received</Confirmation>"))
      when(mockAuditService.sendEvent(any())(any())).thenReturn(Future.successful(Success))

      service.submitToChris(cancelSingleRequest, "credId123", "Agent", fakeAuthRequest).map { result =>
        result must include("Cancel Message received")
      }
    }

    "return confirmation when submission succeeds for VAT" in {
      val enrolments = Enrolments(
        Set(
          Enrolment(
            key               = "VAT",
            identifiers       = Seq(EnrolmentIdentifier("UTR", "1234567890")),
            state             = "Activated",
            delegatedAuthRule = None
          )
        )
      )
      when(mockAuthConnector.authorise(any(), any())(any(), any())).thenReturn(Future.successful(enrolments))
      when(mockConnector.submitEnvelope(any[Elem])).thenReturn(Future.successful("<Confirmation>VAT Message received</Confirmation>"))
      when(mockAuditService.sendEvent(any())(any())).thenReturn(Future.successful(Success))

      service.submitToChris(vatRequest, "credId123", "Agent", fakeAuthRequest).map { result =>
        result must include("VAT Message received")
      }
    }

    "return confirmation when submission succeeds for SDLT" in {
      val enrolments = Enrolments(
        Set(
          Enrolment(
            key               = "VAT",
            identifiers       = Seq(EnrolmentIdentifier("UTR", "1234567890")),
            state             = "Activated",
            delegatedAuthRule = None
          )
        )
      )
      when(mockAuthConnector.authorise(any(), any())(any(), any())).thenReturn(Future.successful(enrolments))
      when(mockConnector.submitEnvelope(any[Elem])).thenReturn(Future.successful("<Confirmation>SDLT Message received</Confirmation>"))
      when(mockAuditService.sendEvent(any())(any())).thenReturn(Future.successful(Success))

      service.submitToChris(ctRequest, "credId123", "Agent", fakeAuthRequest).map { result =>
        result must include("SDLT Message received")
      }
    }

    "return confirmation when submission succeeds for PAYE" in {
      val enrolments = Enrolments(
        Set(
          Enrolment(
            key               = "IR-PAYE",
            identifiers       = Seq(EnrolmentIdentifier("TaxOfficeNumber", "11111111111"), EnrolmentIdentifier("TaxOfficeReference", "222222222222")),
            state             = "Activated",
            delegatedAuthRule = None
          )
        )
      )
      when(mockAuthConnector.authorise(any(), any())(any(), any())).thenReturn(Future.successful(enrolments))
      when(mockConnector.submitEnvelope(any[Elem])).thenReturn(Future.successful("<Confirmation>PAYE Message received</Confirmation>"))
      when(mockAuditService.sendEvent(any())(any())).thenReturn(Future.successful(Success))

      service.submitToChris(payeRequest, "credId123", "Agent", fakeAuthRequest).map { result =>
        result must include("PAYE Message received")
      }
    }

    "return confirmation when submission succeeds for MGD (none)" in {
      val enrolments = Enrolments(
        Set(
          Enrolment(
            key               = "MGD",
            identifiers       = Seq(EnrolmentIdentifier("HMRCMGDRN", "MGD4567890")),
            state             = "Activated",
            delegatedAuthRule = None
          )
        )
      )
      when(mockAuthConnector.authorise(any(), any())(any(), any())).thenReturn(Future.successful(enrolments))
      when(mockConnector.submitEnvelope(any[Elem])).thenReturn(Future.successful("<Confirmation>MGD Message received</Confirmation>"))
      when(mockAuditService.sendEvent(any())(any())).thenReturn(Future.successful(Success))

      service.submitToChris(mgdRequest, "credId123", "Individual", fakeAuthRequest).map { result =>
        result must include("MGD Message received")
      }
    }

    "return confirmation when submission succeeds for cancel variable plan" in {
      val enrolments = Enrolments(
        Set(
          Enrolment(
            key               = "MGD",
            identifiers       = Seq(EnrolmentIdentifier("HMRCMGDRN", "MGD4567890")),
            state             = "Activated",
            delegatedAuthRule = None
          )
        )
      )
      when(mockAuthConnector.authorise(any(), any())(any(), any())).thenReturn(Future.successful(enrolments))
      when(mockConnector.submitEnvelope(any[Elem]))
        .thenReturn(Future.successful("<Confirmation>MGD with variable Cancel Message received</Confirmation>"))
      when(mockAuditService.sendEvent(any())(any())).thenReturn(Future.successful(Success))

      service.submitToChris(mgdCancelRequest, "credId123", "Individual", fakeAuthRequest).map { result =>
        result must include("MGD with variable Cancel Message received")
      }
    }

    "propagate connector failures" in {
      val enrolments = Enrolments(Set(Enrolment("HMRC-NDDS-ORG")))
      when(mockAuthConnector.authorise(any(), any())(any(), any())).thenReturn(Future.successful(enrolments))
      when(mockConnector.submitEnvelope(any[Elem])).thenReturn(Future.failed(new RuntimeException("Boom")))
      when(mockAuditService.sendEvent(any())(any())).thenReturn(Future.successful(Success))

      recoverToSucceededIf[RuntimeException] {
        service.submitToChris(tcRequest, "credId789", "Agent", fakeAuthRequest)
      }
    }
  }
}
