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
import uk.gov.hmrc.nationaldirectdebit.models.SubmissionResult
import uk.gov.hmrc.nationaldirectdebit.models.requests.ChrisSubmissionRequest
import uk.gov.hmrc.nationaldirectdebit.models.requests.chris.DirectDebitSource
import uk.gov.hmrc.nationaldirectdebit.services.ChrisEnvelopeConstants.enrolmentToHodService
import uk.gov.hmrc.nationaldirectdebit.services.chrisUtils.{ChrisEnvelopeBuilder, XmlValidator}
import uk.gov.hmrc.play.audit.http.connector.AuditResult

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ChrisService @Inject() (chrisConnector: ChrisConnector, authConnector: AuthConnector, validator: XmlValidator, auditService: AuditService)(
  implicit ec: ExecutionContext
) extends Logging {

  private def getActiveEnrolmentForKeys(
    serviceType: DirectDebitSource
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[Map[String, String]]] = {

    authConnector.authorise(EmptyPredicate, Retrievals.allEnrolments).map { enrolments =>

      val expectedHodServiceOpt = ChrisEnvelopeConstants.listHodServices.get(serviceType)
      logger.info(s"Expected HOD service for [$serviceType] = ${expectedHodServiceOpt.getOrElse("not found")}")

      val activeEnrolments = enrolments.enrolments.toSeq.filter(_.isActivated)

      // Flatten identifiers per enrolment key
      val grouped: Map[String, Seq[(String, String)]] =
        activeEnrolments
          .groupBy(_.key)
          .view
          .mapValues(_.flatMap(_.identifiers.map(i => i.key -> i.value)))
          .toMap

      // Map enrolment → HOD service → known fact type → per identifier
      val mappedKnownFacts: Seq[Map[String, String]] =
        grouped.toSeq.flatMap { case (enrolmentKey, identifiers) =>
          val maybeHodService =
            ChrisEnvelopeConstants.enrolmentToHodService.get(enrolmentKey)

          val maybeKnownFactType =
            maybeHodService.flatMap(ChrisEnvelopeConstants.hodServiceToKnownFactType.get)

          // No known fact type → no output for this enrolment
          maybeKnownFactType.toSeq.flatMap { knownFactType =>
            // Produce ONE entry per identifier (fixes EMPREF concatenation issue)
            identifiers.map {
              case ("NINO", v) =>
                Map(
                  "knownFactType"  -> knownFactType,
                  "knownFactValue" -> v.take(8)
                )

              case (_, v) =>
                Map(
                  "knownFactType"  -> knownFactType,
                  "knownFactValue" -> v.trim
                )
            }
          }
        }

      // Keep ordering logic: expected HOD service first
      val reordered =
        expectedHodServiceOpt match {
          case Some(expectedHodService) =>
            val expectedKnownFactType =
              ChrisEnvelopeConstants.hodServiceToKnownFactType.getOrElse(expectedHodService, "")

            val (matching, others) =
              mappedKnownFacts.partition(_("knownFactType") == expectedKnownFactType)

            matching ++ others

          case None =>
            mappedKnownFacts
        }

      reordered
    }
  }

  private def knownFactDataWithEnrolment(
    serviceType: DirectDebitSource
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[Map[String, String]]] = {

    authConnector.authorise(EmptyPredicate, Retrievals.allEnrolments).map { enrolments =>

      logger.info(s"Retrieved enrolments: ${enrolments.enrolments.map(_.key).mkString(", ")}")

      val expectedHodService: Option[String] = ChrisEnvelopeConstants.listHodServices.get(serviceType)
      logger.info(s"Expected HOD service for [$serviceType] = ${expectedHodService.getOrElse("not found")}")

      val activeEnrolments: Seq[Enrolment] = enrolments.enrolments.toSeq.filter(_.isActivated)

      val grouped: Map[String, Seq[(String, String)]] =
        activeEnrolments
          .groupBy(_.key)
          .view
          .mapValues(_.flatMap(_.identifiers.map(i => i.key -> i.value)))
          .toMap

      val enrolmentMaps: Seq[Map[String, String]] = grouped.map { case (enrolmentKey, identifiers) =>
        val concatenatedNames = identifiers.map(_._1).mkString("/")
        val concatenatedValues = identifiers
          .map {
            case (name, value) if name == "NINO" => value.take(8)
            case (_, value)                      => value
          }
          .mkString("/")

        val hodServiceName = enrolmentToHodService.getOrElse(enrolmentKey, enrolmentKey)

        Map(
          "service"         -> hodServiceName,
          "identifierName"  -> concatenatedNames,
          "identifierValue" -> concatenatedValues
        )
      }.toSeq

      val (matching, others) = expectedHodService match {
        case Some(expected) =>
          val matchList = enrolmentMaps.filter(_.get("service").contains(expected))
          if (matchList.nonEmpty) {
            logger.info(s"Found matching HOD service [$expected]. It will appear first in XML.")
          } else {
            logger.warn(s"No active enrolment matches expected HOD service [$expected]. Including all in XML.")
          }
          (matchList, enrolmentMaps.filterNot(_.get("service").contains(expected)))
        case None =>
          (Seq.empty, enrolmentMaps)
      }

      val reordered = matching ++ others
      logger.info(s"*** Final enrolment maps for XML (matching first): ${reordered.mkString(", ")}")
      reordered
    }
  }
  import scala.util.{Failure, Success}

  def submitToChris(
    request: ChrisSubmissionRequest,
    credId: String,
    affinityGroup: String
  )(implicit hc: HeaderCarrier): Future[SubmissionResult] = {

    val correlatingId: String = java.util.UUID.randomUUID().toString.replace("-", "")

    for {
      knownFactData <- knownFactDataWithEnrolment(request.serviceType)
      keysData      <- getActiveEnrolmentForKeys(request.serviceType)
      envelopeDetails = ChrisEnvelopeBuilder.getEnvelopeDetails(request, credId, affinityGroup, knownFactData, keysData, correlatingId)
      envelopeXml = ChrisEnvelopeBuilder.build(envelopeDetails)

      // Validate XML
      validationResult = validator.validate(envelopeXml)

      result <- validationResult match {
                  case Failure(e) =>
                    logger.error(s"ChRIS XML validation failed: ${e.getMessage}", e)
                    Future.failed(new RuntimeException(s"XML validation failed: ${e.getMessage}", e))

                  case Success(_) =>
                    logger.info("ChRIS XML validation succeeded. Sending audit before submission...")
                    auditService.sendEvent(envelopeDetails).flatMap {
                      case AuditResult.Success =>
                        logger.info("Audit succeeded. Submitting envelope to ChRIS...")
                        chrisConnector.submitEnvelope(envelopeXml, correlatingId).recoverWith { case e =>
                          logger.error(s"ChRIS submission failed: ${e.getMessage}", e)
                          Future.failed(new RuntimeException(s"ChRIS submission failed: ${e.getMessage}", e))
                        }

                      case AuditResult.Disabled =>
                        logger.error("Audit service returned Disabled result. Submission stopped.")
                        Future.failed(new RuntimeException("Audit service disabled. Submission aborted."))

                      case AuditResult.Failure(msg, _) =>
                        logger.error(s"Audit service failure: $msg. Submission stopped.")
                        Future.failed(new RuntimeException(s"Audit failed: $msg. Submission aborted."))
                    }
                }
    } yield result
  }

}
