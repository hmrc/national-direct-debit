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

import play.api.Logging
import uk.gov.hmrc.nationaldirectdebit.services.ChrisEnvelopeConstants

import scala.xml.{Elem, PrettyPrinter, XML}

object ChrisEnvelopeBuilder extends Logging {

  private val dateTimeFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")
  private val prettyPrinter = new PrettyPrinter(120, 4)

  def build(
    request: uk.gov.hmrc.nationaldirectdebit.models.requests.ChrisSubmissionRequest,
    credId: String,
    affinityGroup: String,
    hodServices: Seq[Map[String, String]],
    keysData: Seq[Map[String, String]]
  ): Elem = {

    val correlatingId = java.util.UUID.randomUUID().toString.replace("-", "")
    val receiptDate = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).format(dateTimeFormatter)
    val submissionDateTime = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).format(dateTimeFormatter)
    val periodEnd = DateUtils.calculatePeriodEnd()
    val senderType = if (affinityGroup == "Agent") "Agent" else "Individual"
    val serviceType = request.serviceType
    val expectedHodService: Option[String] = ChrisEnvelopeConstants.listHodServices.get(serviceType)

    val envelopeXml: Elem =
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
              <Keys>{XmlUtils.formatKeys(keysData, "               ")}</Keys>
              <PeriodEnd>{periodEnd}</PeriodEnd>
              <Sender>{senderType}</Sender>
            </IRheader>
            <dDIPPDetails>
              <submissionDateTime>{submissionDateTime}</submissionDateTime>
              <credentialID>{credId}</credentialID>
                {XmlUtils.formatKnownFacts(hodServices, "           ")}
              <directDebitInstruction>
                {
        if (request.amendPlan || request.cancelPlan || request.suspendPlan || request.removeSuspensionPlan) {
          <ddiReferenceNo>{request.ddiReferenceNo}</ddiReferenceNo>
        } else {
          <actionType>{ChrisEnvelopeConstants.ActionType_1}</actionType>
                          <ddiReferenceNo>{request.ddiReferenceNo}</ddiReferenceNo>
                           <bankSortCode>{request.yourBankDetailsWithAuddisStatus.sortCode}</bankSortCode>
                          <bankAccountNo>{request.yourBankDetailsWithAuddisStatus.accountNumber}</bankAccountNo>
                          <bankAccountName>{request.yourBankDetailsWithAuddisStatus.accountHolderName}</bankAccountName>
        }
      }
                        {if (request.yourBankDetailsWithAuddisStatus.auddisStatus) <paperAuddisFlag>01</paperAuddisFlag> else scala.xml.Null}
              </directDebitInstruction>{PaymentPlanBuilder.build(request, expectedHodService)}
            </dDIPPDetails>
          </IRenvelope>
        </Body>
      </ChRISEnvelope>
    val prettyXmlString = prettyPrinter.format(envelopeXml)
    logger.info(s"Chris Envelope XML:\n$prettyXmlString")
    XML.loadString(prettyXmlString)
  }
}
