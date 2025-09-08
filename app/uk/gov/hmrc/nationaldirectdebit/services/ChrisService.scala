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

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.nationaldirectdebit.connectors.ChrisConnector
import scala.concurrent.{ExecutionContext, Future}
import scala.xml.{Elem, NodeSeq}
import play.api.Logging

@Singleton
class ChrisService @Inject()(
                              connector: ChrisConnector
                            )(implicit ec: ExecutionContext) extends Logging {

  private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")
  private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  /** Build SOAP envelope */
  def buildEnvelope(
                     directDebitData: Map[String, Any],
                     knownFacts: Seq[Map[String, String]],
                     planXml: NodeSeq
                   ): Elem = {
    val correlatingId = UUID.randomUUID().toString.replace("-", "")
    val now = LocalDateTime.now()

    val keysXml: NodeSeq = knownFacts.map { kf =>
      <Key Type={kf("type")}>{kf("value")}</Key>
    }

    <ChRISEnvelope xmlns="http://www.hmrc.gov.uk/ChRIS/Envelope/2">
      <EnvelopeVersion>2.0</EnvelopeVersion>
      <Header>
        <MessageClass>HMRC-NDDS-DDI</MessageClass>
        <Qualifier>request</Qualifier>
        <Function>submit</Function>
        <Sender>
          <System>Portal</System>
          <CorrelatingID>{correlatingId}</CorrelatingID>
          <ReceiptDate>{now.format(dateTimeFormatter)}</ReceiptDate>
        </Sender>
      </Header>
      <Body>
        <IRenvelope xmlns=" add heree some *****">
          <IRheader>
            <Keys>{keysXml}</Keys>
            <PeriodEnd>{directDebitData.getOrElse("periodEnd", "")}</PeriodEnd>
            <Sender>{directDebitData.getOrElse("sender", "Individual")}</Sender>
          </IRheader>
          <dDIPPDetails>
            <submissionDateTime>{now.format(dateTimeFormatter)}</submissionDateTime>
            <credentialID>{directDebitData.getOrElse("credentialID", "")}</credentialID>
            {knownFacts.map { kf =>
            <knownFact>
              <service>{kf("service")}</service>
              <value>{kf("value")}</value>
            </knownFact>
          }}
            <directDebitInstruction>
              <actionType>{directDebitData.getOrElse("actionType", "01")}</actionType>
              <ddiReferenceNo>{directDebitData.getOrElse("ddiReferenceNo", "")}</ddiReferenceNo>
              <bankSortCode>{directDebitData.getOrElse("bankSortCode", "")}</bankSortCode>
              <bankAccountNo>{directDebitData.getOrElse("bankAccountNo", "")}</bankAccountNo>
              <bankAccountName>{directDebitData.getOrElse("bankAccountName", "")}</bankAccountName>
              {if (directDebitData.getOrElse("paperAuddisFlag", "00") == "01") <paperAuddisFlag>01</paperAuddisFlag>}
            </directDebitInstruction>
            <paymentPlan>{planXml}</paymentPlan>
          </dDIPPDetails>
        </IRenvelope>
      </Body>
    </ChRISEnvelope>
  }

  /** Submit data to ChRIS */
  def submit(directDebitData: Map[String, Any], knownFacts: Seq[Map[String, String]], planXml: NodeSeq): Future[String] = {
    val envelope = buildEnvelope(directDebitData, knownFacts, planXml)
    connector.submitEnvelope(envelope)
  }
}
