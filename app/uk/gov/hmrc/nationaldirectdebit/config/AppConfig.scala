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

package uk.gov.hmrc.nationaldirectdebit.config

import play.api.{Configuration, Environment}
import uk.gov.hmrc.nationaldirectdebit.services.chrisUtils.SchemaLoader

import javax.inject.{Inject, Singleton}
import javax.xml.validation.Schema

@Singleton
class AppConfig @Inject() (val config: Configuration, val environment: Environment):

  val appName: String = config.get[String]("appName")

  val schemaNames: Seq[String] =
    config.get[Seq[String]]("microservice.xsd.schemaNames")

  val schema: Schema = SchemaLoader.loadSchemas(schemaNames, environment)

  def baseUrl(serviceName: String): String = {
    val chrisHost = config.get[String](s"microservice.services.$serviceName.host")
    val chrisPort = config.get[String](s"microservice.services.$serviceName.port")
    val chrisProtocol = config.get[String](s"microservice.services.$serviceName.protocol")
    val chrisSubmitURL = config.get[String](s"microservice.services.$serviceName.submissionURL")
    s"$chrisProtocol://$chrisHost:$chrisPort$chrisSubmitURL"
  }
