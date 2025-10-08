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

package uk.gov.hmrc.nationaldirectdebit.connectors

import com.google.inject.Inject
import play.api.Logging
import play.api.libs.ws.DefaultBodyWritables.*
import play.api.libs.ws.ahc.StandaloneAhcWSClient
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}
import scala.xml.Elem

class ChrisConnector @Inject() (
  ws: StandaloneAhcWSClient,
  config: ServicesConfig
)(implicit ec: ExecutionContext)
    extends Logging {

  private val chrisBaseUrl: String = config.baseUrl("chris") + "/submission/ChRIS/NDDS/Filing/async/HMRC-NDDS-DDI"

  def submitEnvelope(envelope: Elem): Future[String] = {
    ws.url(chrisBaseUrl)
      .withHttpHeaders("Content-Type" -> "application/xml")
      .post(envelope.toString())
      .flatMap { response =>
        if (response.status >= 200 && response.status < 300) {
          logger.info(s"ChRIS submission successful: ${response.status}")
          Future.successful(response.body)
        } else {
          val msg = s"ChRIS submission failed with status ${response.status}: ${response.body}"
          logger.error(msg)
          Future.failed(new RuntimeException(msg)) // throws to calling service
        }
      }
  }

}
