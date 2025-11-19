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
import play.api.libs.json.Json
import uk.gov.hmrc.nationaldirectdebit.services.ChrisEnvelopeConstants
import uk.gov.hmrc.nationaldirectdebit.models.requests.ChrisSubmissionRequest
import uk.gov.hmrc.nationaldirectdebit.models.requests.chris.{DirectDebitSource, EnvelopeDetails}

import scala.xml.{Elem, PrettyPrinter, XML}

object ChrisEnvelopeBuilder extends Logging {

  private val dateTimeFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")
  private val prettyPrinter = new PrettyPrinter(120, 4)

  def getEnvelopeDetails(
    request: ChrisSubmissionRequest,
    credId: String,
    affinityGroup: String,
    knownFactData: Seq[Map[String, String]],
    keysData: Seq[Map[String, String]]
  ): EnvelopeDetails = {

    val correlatingId: String = java.util.UUID.randomUUID().toString.replace("-", "")
    val receiptDate: String = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).format(dateTimeFormatter)
    val submissionDateTime: String = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).format(dateTimeFormatter)
    val periodEnd: String = DateUtils.calculatePeriodEnd()
    val senderType: String = if (affinityGroup == "Agent") "Agent" else "Individual"
    val serviceType: DirectDebitSource = request.serviceType
    val expectedHodService: Option[String] = ChrisEnvelopeConstants.listHodServices.get(serviceType)

    EnvelopeDetails(
      request,
      credId,
      knownFactData,
      keysData,
      correlatingId,
      receiptDate,
      submissionDateTime,
      periodEnd,
      senderType,
      serviceType,
      expectedHodService
    )
  }

  def build(envelopeDetails: EnvelopeDetails): Elem = {

    val envelopeXml: Elem =
      <ChRISEnvelope xmlns="http://www.hmrc.gov.uk/ChRIS/Envelope/2">
        <EnvelopeVersion>2.0</EnvelopeVersion>
        <Header>
          <MessageClass>
            {ChrisEnvelopeConstants.MessageClass}
          </MessageClass>
          <Qualifier>
            {ChrisEnvelopeConstants.Qualifier}
          </Qualifier>
          <Function>
            {ChrisEnvelopeConstants.Function}
          </Function>
          <Sender>
            <System>
              {ChrisEnvelopeConstants.SenderSystem}
            </System>
            <CorrelatingID>
              {envelopeDetails.correlatingId}
            </CorrelatingID>
            <ReceiptDate>
              {envelopeDetails.receiptDate}
            </ReceiptDate>
          </Sender>
        </Header>
        <Body>
          <IRenvelope xmlns={""}>
            <IRheader>
              <Keys>
                {XmlUtils.formatKeys(envelopeDetails.keysData)}
              </Keys>
              <PeriodEnd>
                {envelopeDetails.periodEnd}
              </PeriodEnd>
              <Sender>
                {envelopeDetails.senderType}
              </Sender>
            </IRheader>
            <dDIPPDetails>
              <submissionDateTime>
                {envelopeDetails.submissionDateTime}
              </submissionDateTime>
              <credentialID>
                {envelopeDetails.credId}
              </credentialID>{XmlUtils.formatKnownFacts(envelopeDetails.knownFactData)}<directDebitInstruction>
              {
        if (
          envelopeDetails.request.amendPlan || envelopeDetails.request.cancelPlan || envelopeDetails.request.suspendPlan || envelopeDetails.request.removeSuspensionPlan
        ) {
          <ddiReferenceNo>
                  {envelopeDetails.request.ddiReferenceNo}
                </ddiReferenceNo>
        } else {
          <actionType>
                  {ChrisEnvelopeConstants.ActionType_1}
                </actionType>
                  <ddiReferenceNo>
                    {envelopeDetails.request.ddiReferenceNo}
                  </ddiReferenceNo>
                  <bankSortCode>
                    {envelopeDetails.request.yourBankDetailsWithAuddisStatus.sortCode}
                  </bankSortCode>
                  <bankAccountNo>
                    {envelopeDetails.request.yourBankDetailsWithAuddisStatus.accountNumber}
                  </bankAccountNo>
                  <bankAccountName>
                    {envelopeDetails.request.yourBankDetailsWithAuddisStatus.accountHolderName}
                  </bankAccountName>
        }
      }{if (envelopeDetails.request.yourBankDetailsWithAuddisStatus.auddisStatus) <paperAuddisFlag>01</paperAuddisFlag> else scala.xml.Null}
            </directDebitInstruction>{PaymentPlanBuilder.build(envelopeDetails.request, envelopeDetails.expectedHodService)}
            </dDIPPDetails>
          </IRenvelope>
        </Body>
      </ChRISEnvelope>
    val prettyXmlString = prettyPrinter.format(envelopeXml)
    logger.info(s"Chris Envelope XML:\n$prettyXmlString")
    XML.loadString(prettyXmlString)
  }
}
