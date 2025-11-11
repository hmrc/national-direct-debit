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
import uk.gov.hmrc.nationaldirectdebit.services.chrisUtils.{ChrisEnvelopeBuilder, XmlValidator}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ChrisService @Inject() (chrisConnector: ChrisConnector, authConnector: AuthConnector, validator: XmlValidator)(implicit ec: ExecutionContext)
    extends Logging {

  private def getEligibleHodServices(
    request: ChrisSubmissionRequest
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[Map[String, String]]] = {
    authConnector.authorise(EmptyPredicate, Retrievals.allEnrolments).map { enrolments =>

      logger.info(s"Retrieved enrolments: ${enrolments.enrolments.map(_.key).mkString(", ")}")

      val serviceType = request.serviceType
      val expectedHodService: Option[String] = ChrisEnvelopeConstants.listHodServices.get(serviceType)

      logger.info(s"Expected HOD service for [$serviceType] = ${expectedHodService.getOrElse("not found")}")

      // Step 1: Filter active enrolments
      val activeEnrolments: Seq[Enrolment] = enrolments.enrolments.toSeq.filter(_.isActivated)

      // Step 2: Group identifiers by service
      val grouped: Map[String, Seq[(String, String)]] =
        activeEnrolments
          .groupBy(_.key)
          .view
          .mapValues { enrols =>
            enrols.flatMap(_.identifiers.map(i => i.key -> i.value))
          }
          .toMap

      // Step 3: Build final Seq[Map(service, identifierName, identifierValue)]
      val enrolmentMaps: Seq[Map[String, String]] = grouped.map { case (service, identifiers) =>
        val concatenatedNames = identifiers.map(_._1).mkString("/")
        val concatenatedValues = identifiers
          .map { case (name, value) =>
            if (service == "NTS" && name == "NINO") value.take(8) else value
          }
          .mkString("/")

        Map(
          "service"         -> service,
          "identifierName"  -> concatenatedNames,
          "identifierValue" -> concatenatedValues
        )
      }.toSeq

      // Step 4: Reorder so expected HOD service comes first
      val (matching, others) = expectedHodService match {
        case Some(hodKey) =>
          enrolmentMaps.partition(_.get("service").exists(_.contains(hodKey)))
        case None => (Seq.empty, enrolmentMaps)
      }

      val reordered = matching ++ others

      logger.info(s"*** Final enrolment maps (reordered): ${reordered.mkString(", ")}")
      reordered
    }
  }

  import scala.util.{Failure, Success}

  def submitToChris(
    request: ChrisSubmissionRequest,
    credId: String,
    affinityGroup: String
  )(implicit hc: HeaderCarrier): Future[String] = {

    for {
      // Step 1: Build the XML envelope from the request
      hodServices <- getEligibleHodServices(request)
      envelopeXml = ChrisEnvelopeBuilder.build(request, credId, affinityGroup, hodServices)

      // Step 2: Validate the XML (returns Try[Unit])
      validationResult = validator.validate(envelopeXml)

      // Step 3: Handle validation outcome
      result <- validationResult match {
                  case Success(_) =>
                    logger.info("✅ ChRIS XML validation succeeded. Submitting envelope to ChRIS...")
                    chrisConnector.submitEnvelope(envelopeXml)

                  case Failure(e) =>
                    logger.error(s"❌ ChRIS XML validation failed: ${e.getMessage}", e)
                    Future.failed(
                      new RuntimeException(s"ChRIS submission skipped due to invalid XML: ${e.getMessage}", e)
                    )
                }
    } yield result
  }

}
