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

import org.xml.sax.SAXException

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory
import scala.util.{Failure, Success, Try}

object ChRISXmlValidator {

  private val schemaBasePath = "conf/xsd"

  def validate(xmlString: String, schemaName: String): Try[Unit] = {
    val schemaPath = s"$schemaBasePath/$schemaName"

    val factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
    Try {
      val schemaStream = new java.io.FileInputStream(schemaPath)
      val schema = factory.newSchema(new StreamSource(schemaStream))
      val validator = schema.newValidator()
      val xmlStream = new ByteArrayInputStream(xmlString.getBytes(StandardCharsets.UTF_8))
      validator.validate(new StreamSource(xmlStream))
    } match {
      case Success(_) => Success(())
      case Failure(e: SAXException) =>
        Failure(new RuntimeException(s"XML validation failed using [$schemaName]: ${e.getMessage}", e))
      case Failure(e) => Failure(e)
    }
  }
}
