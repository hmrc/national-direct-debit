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

      val expectedHodServiceOpt =
        ChrisEnvelopeConstants.listHodServices.get(serviceType)

      // Only activated enrolments
      val active = enrolments.enrolments.toSeq.filter(_.isActivated)

      // Priority ordering for UTR-based enrolments
      val utrPriority: Map[String, Int] = Map(
        "IR-SA-PART-ORG"  -> 1,
        "IR-SA-TRUST-ORG" -> 2,
        "IR-SA"           -> 3
      ).withDefaultValue(99)

      // Group identifiers correctly per enrolment (fixes key-mixing bug)
      val enrolMap: Map[String, Seq[(String, String)]] =
        active.map(e => e.key -> e.identifiers.map(i => i.key -> i.value.trim)).toMap

      // Map each enrolment → HoD service → knownFact
      val mapped: Seq[(String, String, Int)] =
        enrolMap.toSeq.flatMap { case (enrolKey, identifiers) =>
          ChrisEnvelopeConstants.enrolmentToHodService.get(enrolKey).toSeq.flatMap { hodService =>

            ChrisEnvelopeConstants.hodServiceToKnownFactType.get(hodService).toSeq.map { knownFactType =>

              val value: String =
                if ((hodService == "PAYE" || hodService == "CIS") && knownFactType == "EMPREF") {
                  val ton = identifiers.find(_._1 == "TaxOfficeNumber").map(_._2).getOrElse("")
                  val tor = identifiers.find(_._1 == "TaxOfficeReference").map(_._2).getOrElse("")
                  ton + tor
                } else {
                  identifiers
                    .collectFirst {
                      case ("NINO", v) => v.take(8)
                      case (_, v)      => v
                    }
                    .getOrElse("")
                }

              (knownFactType, value, utrPriority(enrolKey))
            }
          }
        }

      // Deduplicate by knownFactType and apply EMPREF rules
      val deduped: Seq[Map[String, String]] =
        mapped
          .groupBy(_._1) // group by knownFactType
          .toSeq
          .flatMap { case (knownFactType, entries) =>
            val sorted = entries.sortBy(_._3) // apply UTR priority

            if (knownFactType == "EMPREF") {
              // Keep ALL EMPREF entries
              sorted.map { case (_, value, _) =>
                Map("knownFactType" -> knownFactType, "knownFactValue" -> value)
              }
            } else {
              // Keep only highest priority one
              val (_, bestValue, _) = sorted.head
              Seq(Map("knownFactType" -> knownFactType, "knownFactValue" -> bestValue))
            }
          }

      // Reorder to put expected service's knownFactType first
      val reordered: Seq[Map[String, String]] =
        expectedHodServiceOpt match {
          case Some(expectedService) =>
            val expectedKnownFactType =
              ChrisEnvelopeConstants.hodServiceToKnownFactType.getOrElse(expectedService, "")

            val (matching, others) =
              deduped.partition(_("knownFactType") == expectedKnownFactType)

            matching ++ others

          case None =>
            deduped
        }

      reordered
    }
  }

  private def knownFactDataWithEnrolment(
    serviceType: DirectDebitSource
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[Map[String, String]]] = {

    authConnector.authorise(EmptyPredicate, Retrievals.allEnrolments).map { enrolments =>

      logger.info(s"Retrieved enrolments: ${enrolments.enrolments.map(_.key).mkString(", ")}")

      val expectedHodServiceOpt =
        ChrisEnvelopeConstants.listHodServices.get(serviceType)

      logger.info(
        s"Expected HOD service for [$serviceType] = ${expectedHodServiceOpt.getOrElse("not found")}"
      )

      val activeEnrolments = enrolments.enrolments.toSeq.filter(_.isActivated)

      val grouped: Map[String, Seq[(String, String)]] =
        activeEnrolments
          .groupBy(_.key)
          .view
          .mapValues(_.flatMap(_.identifiers.map(i => i.key -> i.value)))
          .toMap

      // --------------------------------------------------------
      // NEW: Collapse all SA enrolments into ONE
      // --------------------------------------------------------
      val saKeys = Seq("IR-SA-PART-ORG", "IR-SA", "IR-SA-TRUST-ORG")

      val firstSaKeyOpt: Option[String] =
        saKeys.find(grouped.contains)

      val cleanedGrouped: Map[String, Seq[(String, String)]] = {
        val withoutAllSA = grouped.filterNot { case (key, _) => saKeys.contains(key) }

        firstSaKeyOpt match {
          case Some(saKey) =>
            withoutAllSA + (saKey -> grouped(saKey)) // Keep only one SA enrolment
          case None =>
            withoutAllSA
        }
      }

      logger.info(s"SA keys originally present: ${grouped.keySet.intersect(saKeys.toSet)}")
      logger.info(s"SA key retained for known fact: ${firstSaKeyOpt.getOrElse("none")}")

      // Build output ONLY for properly mapped enrolments + HOD services
      val mappedFacts: Seq[Map[String, String]] =
        cleanedGrouped.toSeq.flatMap { case (enrolmentKey, identifiers) =>
          ChrisEnvelopeConstants.enrolmentToHodService.get(enrolmentKey).toSeq.flatMap { hodService =>

            ChrisEnvelopeConstants.hodServiceToKnownFactType.get(hodService).toSeq.map { knownFactType =>

              val value: String =
                if ((hodService == "PAYE" || hodService == "CIS") && knownFactType == "EMPREF") {
                  val taxOfficeNumber = identifiers.find(_._1 == "TaxOfficeNumber").map(_._2.trim).getOrElse("")
                  val taxOfficeRef = identifiers.find(_._1 == "TaxOfficeReference").map(_._2.trim).getOrElse("")
                  taxOfficeNumber + taxOfficeRef
                } else {
                  identifiers.headOption
                    .map {
                      case ("NINO", v) => v.take(8)
                      case (_, v)      => v.trim
                    }
                    .getOrElse("")
                }

              Map(
                "service"         -> hodService,
                "identifierName"  -> knownFactType,
                "identifierValue" -> value
              )
            }
          }
        }

      // Reorder: matching first, then all others
      val reordered =
        expectedHodServiceOpt match {
          case Some(expected) =>
            val (matching, others) =
              mappedFacts.partition(_("service") == expected)
            matching ++ others

          case None =>
            mappedFacts
        }

      logger.info(
        s"*** Final enrolment maps for XML (matching first): ${reordered.mkString(", ")}"
      )

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
