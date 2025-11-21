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
import org.mockito.Mockito.*
import org.mockito.ArgumentMatchers.*
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.nationaldirectdebit.config.AppConfig
import uk.gov.hmrc.nationaldirectdebit.services.chrisUtils.{SchemaValidator, XmlValidator}

import scala.util.{Failure, Success}
import scala.xml.XML
import javax.xml.validation.Schema

class XmlValidatorSpec extends AnyWordSpec with Matchers with MockitoSugar {

  "XmlValidator" should {

    "return Success(Unit) when XML is valid" in {
      val mockSchemaValidator = mock[SchemaValidator]
      val mockConfig = mock[AppConfig]
      val mockSchema = mock[Schema]

      when(mockConfig.schema).thenReturn(mockSchema)
      when(mockSchemaValidator.validate(any[String], any[Schema])).thenReturn(true)

      val validator = new XmlValidator(mockConfig, mockSchemaValidator)

      val xml = XML.loadString("<TestRoot><Message>Hello</Message></TestRoot>")

      val result = validator.validate(xml)

      result shouldBe a[Success[_]]
    }

    "return Failure when schemaValidator returns false" in {
      val mockSchemaValidator = mock[SchemaValidator]
      val mockConfig = mock[AppConfig]
      val mockSchema = mock[Schema]

      when(mockConfig.schema).thenReturn(mockSchema)
      when(mockSchemaValidator.validate(any[String], any[Schema])).thenReturn(false)

      val validator = new XmlValidator(mockConfig, mockSchemaValidator)

      val xml = XML.loadString("<TestRoot><Message>Hello</Message></TestRoot>")

      val result = validator.validate(xml)

      result                       shouldBe a[Failure[_]]
      result.failed.get.getMessage shouldBe "XML validation failed against schema"
    }

    "return Failure when schemaValidator throws an exception" in {
      val mockSchemaValidator = mock[SchemaValidator]
      val mockConfig = mock[AppConfig]
      val mockSchema = mock[Schema]

      when(mockConfig.schema).thenReturn(mockSchema)
      when(mockSchemaValidator.validate(any[String], any[Schema]))
        .thenThrow(new RuntimeException("Boom!"))

      val validator = new XmlValidator(mockConfig, mockSchemaValidator)

      val xml = XML.loadString("<TestRoot><Message>Hello</Message></TestRoot>")

      val result = validator.validate(xml)

      result                       shouldBe a[Failure[_]]
      result.failed.get.getMessage shouldBe "Boom!"
    }
  }
}
