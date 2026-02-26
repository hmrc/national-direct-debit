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

package uk.gov.hmrc.nationaldirectdebit.models.requests.chris

import uk.gov.hmrc.nationaldirectdebit.models.requests.ChrisSubmissionRequest
import uk.gov.hmrc.nationaldirectdebit.services.ChrisEnvelopeConstants
import uk.gov.hmrc.nationaldirectdebit.services.chrisUtils.{DateUtils, PaymentPlanBuilder, XmlUtils}

import java.time.LocalDateTime
import java.time.ZoneOffset.UTC
import java.time.format.DateTimeFormatter
import scala.xml.{Elem, PrettyPrinter, XML}

case class EnvelopeDetails(
  request: ChrisSubmissionRequest,
  credId: String,
  knownFactData: Seq[Map[String, String]],
  keysData: Seq[Map[String, String]],
  correlatingId: String,
  receiptDate: String,
  submissionDateTime: String,
  periodEnd: String,
  senderType: String,
  serviceType: DirectDebitSource,
  expectedHodService: Option[String]
) {
  lazy val build: Elem =
    XML.loadString(
      EnvelopeDetails.prettyPrinter.format(
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
            <IRenvelope xmlns={""}>
              <IRheader>
                <Keys>{XmlUtils.formatKeys(keysData)}</Keys>
                <PeriodEnd>{periodEnd}</PeriodEnd>
                <Sender>{senderType}</Sender>
              </IRheader>
              <dDIPPDetails>
                <submissionDateTime>{submissionDateTime}</submissionDateTime>
                <credentialID>{credId}</credentialID>
                {XmlUtils.formatKnownFacts(knownFactData)}
                <directDebitInstruction>
                  {
          if (
            request.amendPlan || request.cancelPlan
            || request.suspendPlan || request.removeSuspensionPlan
            || request.addPlan
          ) {
            <ddiReferenceNo>{request.ddiReferenceNo}</ddiReferenceNo>
          } else {
            <actionType>{ChrisEnvelopeConstants.ActionType_1}</actionType>
                      <ddiReferenceNo>{request.ddiReferenceNo}</ddiReferenceNo>
                      <bankSortCode>{request.yourBankDetailsWithAuddisStatus.sortCode}</bankSortCode>
                      <bankAccountNo>{request.yourBankDetailsWithAuddisStatus.accountNumber}</bankAccountNo>
                      <bankAccountName>{request.yourBankDetailsWithAuddisStatus.accountHolderName}</bankAccountName>
          }
        }{if (request.yourBankDetailsWithAuddisStatus.auddisStatus) <paperAuddisFlag>01</paperAuddisFlag> else scala.xml.Null}
                </directDebitInstruction>{PaymentPlanBuilder.build(request, expectedHodService)}
              </dDIPPDetails>
            </IRenvelope>
          </Body>
        </ChRISEnvelope>
      )
    )
}

object EnvelopeDetails {
  val prettyPrinter = new PrettyPrinter(120, 4)

  def details(request: ChrisSubmissionRequest,
              credId: String,
              affinityGroup: String,
              knownFactData: Seq[Map[String, String]],
              keysData: Seq[Map[String, String]],
              correlatingId: String
             ): EnvelopeDetails = {
    val now = LocalDateTime.now(UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"))
    EnvelopeDetails(
      request,
      credId,
      knownFactData,
      keysData,
      correlatingId,
      now,
      now,
      DateUtils.calculatePeriodEnd(),
      if (affinityGroup == "Agent") "Agent" else "Individual",
      request.serviceType,
      ChrisEnvelopeConstants.listHodServices.get(request.serviceType)
    )
  }
}
