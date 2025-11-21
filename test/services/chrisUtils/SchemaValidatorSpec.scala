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
import uk.gov.hmrc.nationaldirectdebit.services.chrisUtils.{SchemaValidator, ValidationHandler}

import java.nio.file.{Files, Path}
import javax.xml.validation.SchemaFactory
import javax.xml.XMLConstants

class SchemaValidatorSpec extends AnyWordSpec with Matchers {

  private def tempXsd(content: String): Path = {
    val path = Files.createTempFile("test-schema", ".xsd")
    Files.write(path, content.getBytes("UTF-8"))
    path
  }

  private val simpleXsd =
    """<?xml version="1.0" encoding="UTF-8"?>
      |<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
      |    elementFormDefault="qualified">
      |
      |  <xs:element name="TestRoot">
      |    <xs:complexType>
      |      <xs:sequence>
      |        <xs:element name="Message" type="xs:string"/>
      |      </xs:sequence>
      |    </xs:complexType>
      |  </xs:element>
      |
      |</xs:schema>
      |""".stripMargin

  "SchemaValidator" should {

    "return true for valid XML" in {
      val xsdPath = tempXsd(simpleXsd)

      val schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
      val schema = schemaFactory.newSchema(xsdPath.toFile)

      val validXml =
        """<TestRoot>
          |  <Message>Hello</Message>
          |</TestRoot>
          |""".stripMargin

      val validator = new SchemaValidator
      validator.validate(validXml, schema) shouldBe true
    }

    "return false for invalid XML" in {
      val xsdPath = tempXsd(simpleXsd)

      val schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
      val schema = schemaFactory.newSchema(xsdPath.toFile)

      // Message element missing → INVALID
      val invalidXml =
        """<TestRoot>
          |  <!-- Missing <Message> -->
          |</TestRoot>
          |""".stripMargin

      val validator = new SchemaValidator
      validator.validate(invalidXml, schema) shouldBe false
    }

    "record parser errors in ValidationHandler" in {
      val handler = new ValidationHandler
      handler.error(new org.xml.sax.SAXParseException("test error", null))
      handler.error shouldBe true
    }
  }
}
