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

import play.api.{Environment, Logging}

import javax.xml.XMLConstants
import javax.xml.transform.Source
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.{Schema, SchemaFactory}

object SchemaLoader extends Logging {

  def loadSchemas(xsds: Seq[String], env: Environment): Schema = {
    val schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)

    logger.info("🔍 Loading XSD schemas from conf/xsds...")

    val sources: Array[Source] = xsds.map { xsd =>
      val file = env.getFile(s"conf/xsds/$xsd")

      if (!file.exists()) {
        logger.error(s"❌ Missing schema: ${file.getAbsolutePath}")
        throw new RuntimeException(s"Schema file not found: ${file.getAbsolutePath}")
      } else {
        logger.info(s"✅ Found schema: ${file.getAbsolutePath}")
      }

      new StreamSource(file)
    }.toArray

    schemaFactory.newSchema(sources)
  }
}
