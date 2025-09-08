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

import uk.gov.hmrc.nationaldirectdebit.connectors.ChrisConnector
import uk.gov.hmrc.nationaldirectdebit.models.requests.ChrisSubmissionRequest
import uk.gov.hmrc.nationaldirectdebit.models.requests.chris.DirectDebitSource

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime, ZoneOffset}
import java.util.UUID
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.xml.Elem

class ChrisService @Inject()(chrisConnector: ChrisConnector)(implicit ec: ExecutionContext) {

  private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")

  def submitToChris(request: ChrisSubmissionRequest, credId: String, affinityGroup: String): Future[String] = {
    val envelopeXml = buildEnvelopeXml(request, credId, affinityGroup)
    chrisConnector.submitEnvelope(envelopeXml)
  }

  private def buildEnvelopeXml(request: ChrisSubmissionRequest, credId: String, affinityGroup: String): Elem = {
    val correlatingId = UUID.randomUUID().toString.replace("-", "")
    val receiptDate = LocalDateTime.now(ZoneOffset.UTC).format(dateTimeFormatter)
    val submissionDateTime = LocalDateTime.now(ZoneOffset.UTC).format(dateTimeFormatter)
    val periodEnd = calculatePeriodEnd()
    val senderType = if (affinityGroup == "agent") "Agent" else "Individual"

    <ChRISEnvelope xmlns="http://www.hmrc.gov.uk/ChRIS/Envelope/2">
      <EnvelopeVersion>2.0</EnvelopeVersion>
      <Header>
        <MessageClass>HMRC-NDDS-DDI</MessageClass>
        <Qualifier>request</Qualifier>
        <Function>submit</Function>
        <Sender>
          <System>Portal</System>
          <CorrelatingID>{correlatingId}</CorrelatingID>
          <ReceiptDate>{receiptDate}</ReceiptDate>
        </Sender>
      </Header>
      <Body>
        <IRenvelope xmlns=" add ask for value">
          <IRheader>
            <Keys>
              <!-- TODO: populate known facts as <Key Type="...">value</Key> -->
            </Keys>
            <PeriodEnd>{periodEnd}</PeriodEnd>
            <Sender>{senderType}</Sender>
          </IRheader>
          <dDIPPDetails>
            <submissionDateTime>{submissionDateTime}</submissionDateTime>
            <credentialID>{credId}</credentialID>
            <!-- TODO: populate knownFact elements here -->
            <directDebitInstruction>
              <actionType>01</actionType>
              <ddiReferenceNo>{request.ddiReferenceNo}</ddiReferenceNo>
              <bankSortCode>{request.yourBankDetailsWithAuddisStatus.sortCode}</bankSortCode>
              <bankAccountNo>{request.yourBankDetailsWithAuddisStatus.accountNumber}</bankAccountNo>
              <bankAccountName>{request.bankName}</bankAccountName>
              {if (request.yourBankDetailsWithAuddisStatus.auddisStatus) <paperAuddisFlag>01</paperAuddisFlag> else scala.xml.Null}
            </directDebitInstruction>
            {buildPaymentPlanXml(request)}
          </dDIPPDetails>
        </IRenvelope>
      </Body>
    </ChRISEnvelope>
  }

  private def calculatePeriodEnd(): String = {
    val now = LocalDate.now()
    val currentYear = now.getYear
    val taxYearStart = LocalDate.of(currentYear, 4, 6)
    val periodDate = if (now.isBefore(taxYearStart)) taxYearStart else taxYearStart.plusYears(1)
    periodDate.toString
  }

  private def buildPaymentPlanXml(request: ChrisSubmissionRequest): Elem = request.serviceType match {
    case DirectDebitSource.CT =>
      <paymentPlan>
        <actionType>01</actionType>
        <pPType>01</pPType>
        <paymentReference>{request.paymentReference.getOrElse("")}</paymentReference>
        <hodService>CT</hodService>
        <paymentCurrency>GBP</paymentCurrency>
        <scheduledPaymentAmount>{request.paymentAmount.getOrElse(BigDecimal(0))}</scheduledPaymentAmount>
        <scheduledPaymentStartDate>{request.paymentDate.map(_.enteredDate).getOrElse("")}</scheduledPaymentStartDate>
        <totalLiability>{request.totalAmountDue.getOrElse(BigDecimal(0))}</totalLiability>
      </paymentPlan>

    case DirectDebitSource.TC =>
      <paymentPlan>
        <actionType>01</actionType>
        <pPType>03</pPType>
        <paymentReference>{request.paymentReference.getOrElse("")}</paymentReference>
        <hodService>TC</hodService>
        <paymentCurrency>GBP</paymentCurrency>
        <scheduledPaymentAmount>{request.regularPaymentAmount.getOrElse(BigDecimal(0))}</scheduledPaymentAmount>
        <scheduledPaymentStartDate>{request.planStartDate.map(_.enteredDate).getOrElse("")}</scheduledPaymentStartDate>
        <scheduledPaymentEndDate>{request.planEndDate.getOrElse("")}</scheduledPaymentEndDate>
        <scheduledPaymentFrequency>05</scheduledPaymentFrequency>
        <balancingPaymentAmount>{request.totalAmountDue.getOrElse(BigDecimal(0)) - request.regularPaymentAmount.getOrElse(BigDecimal(0))}</balancingPaymentAmount>
        <balancingPaymentDate>{request.planEndDate.getOrElse("")}</balancingPaymentDate>
        <totalLiability>{request.totalAmountDue.getOrElse(BigDecimal(0))}</totalLiability>
      </paymentPlan>

    case DirectDebitSource.MGD =>
      <paymentPlan>
        <actionType>01</actionType>
        <pPType>04</pPType>
        <paymentReference>{request.paymentReference.getOrElse("")}</paymentReference>
        <hodService>MGD</hodService>
        <paymentCurrency>GBP</paymentCurrency>
        <scheduledPaymentStartDate>{request.planStartDate.map(_.enteredDate).getOrElse("")}</scheduledPaymentStartDate>
      </paymentPlan>

    case DirectDebitSource.SA =>
      <paymentPlan>
        <actionType>01</actionType>
        <pPType>02</pPType>
        <paymentReference>{request.paymentReference.getOrElse("")}</paymentReference>
        <hodService>SA</hodService>
        <paymentCurrency>GBP</paymentCurrency>
        <scheduledPaymentAmount>{request.regularPaymentAmount.getOrElse(BigDecimal(0))}</scheduledPaymentAmount>
        <scheduledPaymentStartDate>{request.planStartDate.map(_.enteredDate).getOrElse("")}</scheduledPaymentStartDate>
        <scheduledPaymentEndDate>{request.planEndDate.getOrElse("")}</scheduledPaymentEndDate>
        <scheduledPaymentFrequency>05</scheduledPaymentFrequency>
        <totalLiability>{request.totalAmountDue.getOrElse(BigDecimal(0))}</totalLiability>
      </paymentPlan>

    case _ =>
      <paymentPlan>
        <actionType>01</actionType>
        <pPType>01</pPType>
        <paymentReference>{request.paymentReference.getOrElse("")}</paymentReference>
        <hodService>{request.serviceType.toString}</hodService>
        <paymentCurrency>GBP</paymentCurrency>
      </paymentPlan>
  }
}
