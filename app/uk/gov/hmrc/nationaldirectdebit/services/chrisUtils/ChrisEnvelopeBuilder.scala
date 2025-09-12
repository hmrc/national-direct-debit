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

package uk.gov.hmrc.nationaldirectdebit.services.chrisUtils

import uk.gov.hmrc.nationaldirectdebit.models.requests.ChrisSubmissionRequest
import uk.gov.hmrc.nationaldirectdebit.services.ChrisEnvelopeConstants

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneOffset}
import java.util.UUID
import scala.xml.Elem

object ChrisEnvelopeBuilder {

  private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")

  def build(request: ChrisSubmissionRequest,
            credId: String,
            affinityGroup: String,
            hodServices: Seq[Map[String, String]]): Elem = {

    val correlatingId = UUID.randomUUID().toString.replace("-", "")
    val receiptDate = LocalDateTime.now(ZoneOffset.UTC).format(dateTimeFormatter)
    val submissionDateTime = LocalDateTime.now(ZoneOffset.UTC).format(dateTimeFormatter)
    val periodEnd = DateUtils.calculatePeriodEnd()
    val senderType = if (affinityGroup == "agent") "Agent" else "Individual"

    <ChRISEnvelope xmlns="http://www.hmrc.gov.uk/ChRIS/Envelope/2">
      <EnvelopeVersion>2.0</EnvelopeVersion>
      <Header>
        <MessageClass>{ChrisEnvelopeConstants.MessageClass}</MessageClass>
        <Qualifier>{ChrisEnvelopeConstants.Qualifier}</Qualifier>
        <Function>{ChrisEnvelopeConstants.Function}</Function>
        <Sender>
          <System>{ChrisEnvelopeConstants.SenderSystem}</System>
          <CorrelatingID>{correlatingId}</CorrelatingID>
          <ReceiptDate>{receiptDate}</ReceiptDate>
        </Sender>
      </Header>
      <Body>
        <IRenvelope>
          <IRheader>
            <Keys>
              {XmlUtils.formatKeys(hodServices, "               ")}
            </Keys>
            <PeriodEnd>{periodEnd}</PeriodEnd>
            <Sender>{senderType}</Sender>
          </IRheader>
          <dDIPPDetails>
            <submissionDateTime>{submissionDateTime}</submissionDateTime>
            <credentialID>{credId}</credentialID>
            {XmlUtils.formatKnownFacts(hodServices, "             ")}
            <directDebitInstruction>
              <actionType>{ChrisEnvelopeConstants.ActionType_1}</actionType>
              <ddiReferenceNo>{request.ddiReferenceNo}</ddiReferenceNo>
              <bankSortCode>{request.yourBankDetailsWithAuddisStatus.sortCode}</bankSortCode>
              <bankAccountNo>{request.yourBankDetailsWithAuddisStatus.accountNumber}</bankAccountNo>
              <bankAccountName>{request.bankName}</bankAccountName>
              { if (request.yourBankDetailsWithAuddisStatus.auddisStatus) <paperAuddisFlag>01</paperAuddisFlag> else scala.xml.Null }
            </directDebitInstruction>
            { PaymentPlanBuilder.build(request, request.serviceType.toString) }
          </dDIPPDetails>
        </IRenvelope>
      </Body>
    </ChRISEnvelope>
  }
}
