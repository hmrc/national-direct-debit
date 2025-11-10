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

import uk.gov.hmrc.nationaldirectdebit.config.AppConfig
import play.api.Logging

import javax.inject.{Inject, Singleton}
import scala.util.{Failure, Try}
import scala.xml.NodeSeq

@Singleton
class XmlValidator @Inject() (appConfig: AppConfig, schemaValidator: SchemaValidator) extends Logging {

  def validate(xml: NodeSeq): Try[Unit] = Try {
    val envelope = xml.mkString

    logger.info("🔍 Checking XSD schema availability...")

    appConfig.schemaNames.foreach { name =>
      val file = appConfig.environment.getFile(s"conf/xsds/$name")
      if (!file.exists())
        logger.error(s"❌ Missing schema: ${file.getAbsolutePath}")
      else
        logger.info(s"✅ Found schema: ${file.getAbsolutePath}")
    }

    val isValid = schemaValidator.validate(envelope, appConfig.schema)

    if (!isValid) {
      logger.error("❌ XML validation failed against schema")
      throw new RuntimeException("XML validation failed against schema")
    }

    logger.info("✅ XML validated successfully against schema")
  } recoverWith { case ex =>
    logger.error("⚠️ XML validation failed due to exception", ex)
    Failure(ex)
  }
}
