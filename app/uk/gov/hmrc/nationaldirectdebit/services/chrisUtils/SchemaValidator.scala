/*
 * Copyright 2023 HM Revenue & Customs
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

import org.xml.sax.{ErrorHandler, SAXParseException}
import play.api.{Logger, Logging}

import java.io.StringReader
import javax.inject.Singleton
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.Schema

class ValidationHandler extends ErrorHandler with Logging {

  var error = false

  override def warning(e: SAXParseException): Unit = {
    logger.warn(s"SAX Warning $e")
    error = true
  }

  override def error(e: SAXParseException): Unit = {
    logger.warn(s"SAX Error $e")
    error = true
  }

  override def fatalError(e: SAXParseException): Unit = {
    logger.warn(s"SAX Fatal Error $e")
    error = true
  }
}

@Singleton
class SchemaValidator {

  def validate(xml: String, schema: Schema): Boolean = {
    val validator = schema.newValidator()
    val handler = new ValidationHandler
    validator.setErrorHandler(handler)
    validator.validate(new StreamSource(new StringReader(xml)))
    !handler.error
  }

}
