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
import play.api.libs.ws.DefaultBodyWritables.writeableOf_String
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}
import uk.gov.hmrc.nationaldirectdebit.config.AppConfig
import uk.gov.hmrc.nationaldirectdebit.models.{FATAL_ERROR, SUBMITTED, SubmissionResult}

import scala.concurrent.{ExecutionContext, Future}
import scala.xml.Elem

class ChrisConnector @Inject() (
  httpClient: HttpClientV2,
  appConfig: AppConfig
)(implicit ec: ExecutionContext)
    extends Logging {

  private val chrisBaseUrl: String = appConfig.baseUrl("chris")

  import uk.gov.hmrc.http.HttpReads.Implicits.*

  import scala.util.control.NonFatal

  def submitEnvelope(envelope: Elem, correlationId: String)(implicit hc: HeaderCarrier): Future[SubmissionResult] = {

    val xmlString =
      """<?xml version="1.0" encoding="UTF-8"?>""" + "\n" + envelope.toString()

    httpClient
      .post(url"$chrisBaseUrl")
      .setHeader(
        "Content-Type"  -> "application/xml",
        "Accept"        -> "application/xml",
        "CorrelationId" -> correlationId
      )
      .withBody(xmlString)
      .execute[HttpResponse]
      .map { resp =>
        if (is2xx(resp.status)) {
          logger.info(s"[ChrisConnector] corrId=$correlationId status=${resp.status}")
          SubmissionResult(
            status = SUBMITTED,
            rawXml = Some(resp.body),
            meta   = None
          )
        } else {
          logger.error(s"[ChrisConnector] NON-2xx corrId=$correlationId status=${resp.status} response-body:\n${resp.body}")
          SubmissionResult(
            status = FATAL_ERROR,
            rawXml = Some(resp.body),
            meta   = None
          )
        }
      }
      .recover { case NonFatal(e) =>
        logger.error(
          s"[ChrisConnector] Transport exception calling $chrisBaseUrl corrId=$correlationId",
          e
        )
        SubmissionResult(
          status = FATAL_ERROR,
          rawXml = Some("<connection-error/>"),
          meta   = None
        )
      }
  }

  private def is2xx(status: Int): Boolean = status >= 200 && status < 300
}
