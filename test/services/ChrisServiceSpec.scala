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
import uk.gov.hmrc.auth.core.{AuthConnector, Enrolment, Enrolments}
import uk.gov.hmrc.http.{HeaderCarrier, SessionId}
import uk.gov.hmrc.nationaldirectdebit.connectors.ChrisConnector
import uk.gov.hmrc.nationaldirectdebit.models.requests.*
import uk.gov.hmrc.nationaldirectdebit.models.requests.chris.*
import uk.gov.hmrc.nationaldirectdebit.services.ChrisService

import java.time.LocalDate
import scala.concurrent.Future
import scala.xml.Elem

class ChrisServiceSpec
  extends AsyncWordSpec
    with Matchers
    with ScalaFutures
    with MockitoSugar {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  private val mockConnector     = mock[ChrisConnector]
  private val mockAuthConnector = mock[AuthConnector]

  private val service = new ChrisService(mockConnector, mockAuthConnector)

  // Plan and payment details
  private val planStartDateDetails = PlanStartDateDetails(
    enteredDate = LocalDate.of(2025, 9, 1),
    earliestPlanStartDate = "2025-09-01"
  )

  private val paymentDateDetails = PaymentDateDetails(
    enteredDate = LocalDate.of(2025, 9, 15),
    earliestPaymentDate = "2025-09-01"
  )

  // All test ChrisSubmissionRequests
  private val submissionRequests: Seq[ChrisSubmissionRequest] = Seq(
    ChrisSubmissionRequest(
      serviceType = DirectDebitSource.TC,
      paymentPlanType = PaymentPlanType.TaxCreditRepaymentPlan,
      paymentFrequency = Some(PaymentsFrequency.Monthly),
      yourBankDetailsWithAuddisStatus = YourBankDetailsWithAuddisStatus(
        accountHolderName = "Test",
        sortCode = "123456",
        accountNumber = "12345678",
        auddisStatus = false,
        accountVerified = false
      ),
      planStartDate = Some(planStartDateDetails),
      planEndDate = None,
      paymentDate = Some(paymentDateDetails),
      yearEndAndMonth = None,
      bankDetailsAddress = BankAddress(
        lines = Seq("line 1"),
        town = "Town",
        country = Country("UK"),
        postCode = "NE5 2DH"
      ),
      ddiReferenceNo = "DDI123456789",
      paymentReference = "testReference",
      bankName = "Barclays",
      totalAmountDue = Some(BigDecimal(200)),
      paymentAmount = Some(BigDecimal(100.00)),
      regularPaymentAmount = Some(BigDecimal(90.00)),
      calculation = None
    ),
    ChrisSubmissionRequest(
      serviceType = DirectDebitSource.SA,
      paymentPlanType = PaymentPlanType.BudgetPaymentPlan,
      paymentFrequency = Some(PaymentsFrequency.Monthly),
      yourBankDetailsWithAuddisStatus = YourBankDetailsWithAuddisStatus(
        accountHolderName = "Test",
        sortCode = "123456",
        accountNumber = "12345678",
        auddisStatus = false,
        accountVerified = false
      ),
      planStartDate = Some(planStartDateDetails),
      planEndDate = None,
      paymentDate = Some(paymentDateDetails),
      yearEndAndMonth = None,
      bankDetailsAddress = BankAddress(
        lines = Seq("line 1"),
        town = "Town",
        country = Country("UK"),
        postCode = "NE5 2DH"
      ),
      ddiReferenceNo = "DDI123456789",
      paymentReference = "testReference",
      bankName = "Barclays",
      totalAmountDue = Some(BigDecimal(200)),
      paymentAmount = Some(BigDecimal(100.00)),
      regularPaymentAmount = Some(BigDecimal(90.00)),
      calculation = None
    ),
    ChrisSubmissionRequest(
      serviceType = DirectDebitSource.SA,
      paymentPlanType = PaymentPlanType.BudgetPaymentPlan,
      paymentFrequency = Some(PaymentsFrequency.Weekly),
      yourBankDetailsWithAuddisStatus = YourBankDetailsWithAuddisStatus(
        accountHolderName = "Test",
        sortCode = "123456",
        accountNumber = "12345678",
        auddisStatus = false,
        accountVerified = false
      ),
      planStartDate = Some(planStartDateDetails),
      planEndDate = None,
      paymentDate = Some(paymentDateDetails),
      yearEndAndMonth = None,
      bankDetailsAddress = BankAddress(
        lines = Seq("line 1"),
        town = "Town",
        country = Country("UK"),
        postCode = "NE5 2DH"
      ),
      ddiReferenceNo = "DDI123456789",
      paymentReference = "testReference",
      bankName = "Barclays",
      totalAmountDue = Some(BigDecimal(200)),
      paymentAmount = Some(BigDecimal(100.00)),
      regularPaymentAmount = Some(BigDecimal(90.00)),
      calculation = None
    ),
    ChrisSubmissionRequest(
      serviceType = DirectDebitSource.CT,
      paymentPlanType = PaymentPlanType.SinglePayment,
      paymentFrequency = Some(PaymentsFrequency.Monthly),
      yourBankDetailsWithAuddisStatus = YourBankDetailsWithAuddisStatus(
        accountHolderName = "Test",
        sortCode = "123456",
        accountNumber = "12345678",
        auddisStatus = false,
        accountVerified = false
      ),
      planStartDate = Some(planStartDateDetails),
      planEndDate = None,
      paymentDate = Some(paymentDateDetails),
      yearEndAndMonth = None,
      bankDetailsAddress = BankAddress(
        lines = Seq("line 1"),
        town = "Town",
        country = Country("UK"),
        postCode = "NE5 2DH"
      ),
      ddiReferenceNo = "DDI123456789",
      paymentReference = "testReference",
      bankName = "Barclays",
      totalAmountDue = Some(BigDecimal(200)),
      paymentAmount = Some(BigDecimal(100.00)),
      regularPaymentAmount = Some(BigDecimal(90.00)),
      calculation = None
    ),
    ChrisSubmissionRequest(
      serviceType = DirectDebitSource.MGD,
      paymentPlanType = PaymentPlanType.VariablePaymentPlan,
      paymentFrequency = None,
      yourBankDetailsWithAuddisStatus = YourBankDetailsWithAuddisStatus(
        accountHolderName = "Test",
        sortCode = "123456",
        accountNumber = "12345678",
        auddisStatus = false,
        accountVerified = false
      ),
      planStartDate = Some(planStartDateDetails),
      planEndDate = None,
      paymentDate = Some(paymentDateDetails),
      yearEndAndMonth = None,
      bankDetailsAddress = BankAddress(
        lines = Seq("line 1"),
        town = "Town",
        country = Country("UK"),
        postCode = "NE5 2DH"
      ),
      ddiReferenceNo = "DDI123456789",
      paymentReference = "testReference",
      bankName = "Barclays",
      totalAmountDue = Some(BigDecimal(200)),
      paymentAmount = Some(BigDecimal(100.00)),
      regularPaymentAmount = Some(BigDecimal(90.00)),
      calculation = None
    )
  )

  val fakeAuthRequest = AuthenticatedRequest(
    request = FakeRequest(), // just a placeholder
    internalId = "internalId-123",
    sessionId = SessionId("session-123"),
    credId = "credId123",
    affinityGroup = "Organisation",
    nino = Some("AB123456C")
  )
  
  "ChrisService.submitToChris" should {

    // Run a test for each submissionRequest
    submissionRequests.foreach { req =>
      val freqStr = req.paymentFrequency.map(_.toString.toLowerCase).getOrElse("none")
      s"return confirmation when submission succeeds for ${req.serviceType.toString.toLowerCase} ($freqStr)" in {
        val enrolments = Enrolments(Set(Enrolment("HMRC-NDDS-ORG")))

        when(mockAuthConnector.authorise(any(), any())(any(), any()))
          .thenReturn(Future.successful(enrolments))

        when(mockConnector.submitEnvelope(any[Elem]))
          .thenReturn(Future.successful("<Confirmation>Message received</Confirmation>"))

        service.submitToChris(req, "credId123", "Organisation", fakeAuthRequest).map { result =>
          result must include("Message received")
        }
      }
    }

    "work when no enrolments are returned" in {
      val enrolments = Enrolments(Set.empty)

      when(mockAuthConnector.authorise(any(), any())(any(), any()))
        .thenReturn(Future.successful(enrolments))

      when(mockConnector.submitEnvelope(any[Elem]))
        .thenReturn(Future.successful("<Confirmation>No enrolments</Confirmation>"))

      service.submitToChris(submissionRequests.head, "credId456", "Individual", fakeAuthRequest).map { result =>
        result must include("No enrolments")
      }
    }

    "propagate connector failures" in {
      val enrolments = Enrolments(Set(Enrolment("HMRC-NDDS-ORG")))

      when(mockAuthConnector.authorise(any(), any())(any(), any()))
        .thenReturn(Future.successful(enrolments))

      when(mockConnector.submitEnvelope(any[Elem]))
        .thenReturn(Future.failed(new RuntimeException("Boom")))

      recoverToSucceededIf[RuntimeException] {
        service.submitToChris(submissionRequests.head, "credId789", "Organisation", fakeAuthRequest)
      }
    }
  }
}
