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

package services.chrisUtils

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import javax.xml.validation.SchemaFactory
import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import java.io.File
import uk.gov.hmrc.nationaldirectdebit.services.chrisUtils.SchemaValidator

class SchemaValidatorSpec extends AnyWordSpec with Matchers {

  private def loadSchema(): javax.xml.validation.Schema = {
    val xsdFiles = Seq(
      "/xsd/DDIPPDetails_v1.xsd",
      "/xsd/codelist-ISO4217-v0-3.xsd",
      "/xsd/FinancialTypes-v1-1.xsd",
      "/xsd/FinancialIdentifierTypes-v1-0.xsd"
    ).map(path => new StreamSource(new File(getClass.getResource(path).toURI)))

    val schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)

    schemaFactory.newSchema(xsdFiles.toArray.map(_.asInstanceOf[javax.xml.transform.Source]))
  }

  private def validateXml(xml: String, schema: javax.xml.validation.Schema): Boolean = {
    val validator = new SchemaValidator
    validator.validate(xml, schema)
  }

  private val schema = loadSchema()

  "SchemaValidator" should {

    "validate XML against all related XSDs from resources" in {
      val validXml =
        """<?xml version="1.0" encoding="UTF-8"?>
          |<ChRISEnvelope xmlns="http://www.hmrc.gov.uk/ChRIS/Envelope/2">
          |  <EnvelopeVersion>1.0</EnvelopeVersion>
          |  <Header>
          |    <MessageClass>HMRC-XXXX-DDI</MessageClass>
          |    <Qualifier>request</Qualifier>
          |    <Function>submit</Function>
          |    <Sender>
          |      <System>Portal</System>
          |      <CorrelatingID>3606fc37ce644ffd95007d1af915b60e</CorrelatingID>
          |      <ReceiptDate>2025-11-21T16:30:56.895</ReceiptDate>
          |    </Sender>
          |  </Header>
          |  <Body>
          |    <IRenvelope xmlns="http://www.hmrc.gov.uk/ChRIS/Envelope/2">
          |      <IRheader>
          |        <Keys>
          |          <Key Type="UTR">utr123</Key>
          |        </Keys>
          |        <PeriodEnd>2026-04-06</PeriodEnd>
          |        <Sender>Individual</Sender>
          |      </IRheader>
          |      <dDIPPDetails>
          |        <submissionDateTime>2025-11-21T16:30:56.895</submissionDateTime>
          |        <credentialID>0000000009000202</credentialID>
          |        <knownFact>
          |          <service>CESA</service>
          |          <value>utr123</value>
          |        </knownFact>
          |        <directDebitInstruction>
          |          <ddiReferenceNo>99055025</ddiReferenceNo>
          |          <paperAuddisFlag>01</paperAuddisFlag>
          |        </directDebitInstruction>
          |        <paymentPlan>
          |          <actionType>04</actionType>
          |          <pPType>02</pPType>
          |          <paymentReference>1400256374K</paymentReference>
          |          <corePPReferenceNo>200000801</corePPReferenceNo>
          |          <hodService>CESA</hodService>
          |          <paymentCurrency>GBP</paymentCurrency>
          |          <scheduledPaymentAmount>100.00</scheduledPaymentAmount>
          |          <scheduledPaymentStartDate>2025-11-25</scheduledPaymentStartDate>
          |          <scheduledPaymentFrequency>05</scheduledPaymentFrequency>
          |        </paymentPlan>
          |      </dDIPPDetails>
          |    </IRenvelope>
          |  </Body>
          |</ChRISEnvelope>
          |""".stripMargin

      validateXml(validXml, schema) shouldBe true
    }

    "fail validation if knownFact service exceeds 4 characters" in {
      val invalidXml =
        """<?xml version="1.0" encoding="UTF-8"?>
          |<ChRISEnvelope xmlns="http://www.hmrc.gov.uk/ChRIS/Envelope/2">
          |  <EnvelopeVersion>1.0</EnvelopeVersion>
          |  <Header>
          |    <MessageClass>HMRC-XXXX-DDI</MessageClass>
          |    <Qualifier>request</Qualifier>
          |    <Function>submit</Function>
          |    <Sender>
          |      <System>Portal</System>
          |      <CorrelatingID>3606fc37ce644ffd95007d1af915b60e</CorrelatingID>
          |      <ReceiptDate>2025-11-21T16:30:56.895</ReceiptDate>
          |    </Sender>
          |  </Header>
          |  <Body>
          |    <IRenvelope xmlns="http://www.hmrc.gov.uk/ChRIS/Envelope/2">
          |      <IRheader>
          |        <Keys>
          |          <Key Type="UTR">utr123</Key>
          |        </Keys>
          |        <PeriodEnd>2026-04-06</PeriodEnd>
          |        <Sender>Individual</Sender>
          |      </IRheader>
          |      <dDIPPDetails>
          |        <submissionDateTime>2025-11-21T16:30:56.895</submissionDateTime>
          |        <credentialID>0000000009000202</credentialID>
          |        <knownFact>
          |          <service>CESA12345</service>
          |          <value>utr123</value>
          |        </knownFact>
          |      </dDIPPDetails>
          |    </IRenvelope>
          |  </Body>
          |</ChRISEnvelope>
          |""".stripMargin

      validateXml(invalidXml, schema) shouldBe false
    }
  }
}
