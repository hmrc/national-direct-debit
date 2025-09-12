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

package uk.gov.hmrc.nationaldirectdebit.services

import play.api.Logging
import uk.gov.hmrc.auth.core.authorise.EmptyPredicate
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.{AuthConnector, Enrolment}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.nationaldirectdebit.connectors.ChrisConnector
import uk.gov.hmrc.nationaldirectdebit.models.requests.ChrisSubmissionRequest
import uk.gov.hmrc.nationaldirectdebit.services.chrisUtils.ChrisEnvelopeBuilder

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ChrisService @Inject()(chrisConnector: ChrisConnector,
                             authConnector: AuthConnector
                            )(implicit ec: ExecutionContext) extends Logging {


  private def getEligibleHodServices()
                                    (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[Map[String, String]]] = {
    authConnector.authorise(EmptyPredicate, Retrievals.allEnrolments).map { enrolments =>

      logger.info(s"Retrieved enrolments: ${enrolments.enrolments.map(_.key).mkString(", ")}")

      // Step 1: Filter active enrolments
      val activeEnrolments: Seq[Enrolment] = enrolments.enrolments.toSeq.filter(_.isActivated)

      // Step 2: Build Seq[Map(enrolmentKey -> identifierString)]
      val enrolmentMaps: Seq[Map[String, String]] = activeEnrolments.map { e =>
        val identifierString = e.identifiers.map(_.value).mkString("/") // join values like 222/CC222
        logger.info(s"*****Active enrolment: ${e.key} -> $identifierString")
        Map(e.key -> identifierString)
      }

      logger.info(s"***Final enrolment maps: ${enrolmentMaps.mkString(", ")}")
      enrolmentMaps
    }
  }

  def submitToChris(request: ChrisSubmissionRequest, credId: String, affinityGroup: String)
                   (implicit hc: HeaderCarrier): Future[String] =
    for {
      hodServices <- getEligibleHodServices()
      envelopeXml = ChrisEnvelopeBuilder.build(request, credId, affinityGroup, hodServices)
      result <- chrisConnector.submitEnvelope(envelopeXml)
    } yield result

}
