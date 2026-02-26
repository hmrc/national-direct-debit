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
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.nationaldirectdebit.connectors.ChrisConnector
import uk.gov.hmrc.nationaldirectdebit.models.{FATAL_ERROR as SubmissionStatusFatalError, SUBMITTED as SubmissionStatusSubmitted, SubmissionResult}
import uk.gov.hmrc.nationaldirectdebit.models.requests.{AuthenticatedRequest, ChrisSubmissionRequest}
import uk.gov.hmrc.nationaldirectdebit.models.requests.chris.{DirectDebitSource, EnvelopeDetails}
import uk.gov.hmrc.nationaldirectdebit.services.chrisUtils.SchemaValidator
import uk.gov.hmrc.play.audit.http.connector.AuditResult
import scala.util.{Failure, Success}

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ChrisService @Inject() (chrisConnector: ChrisConnector, validator: SchemaValidator, auditService: AuditService)(implicit ec: ExecutionContext)
    extends Logging {

  // TODO
  private def getActiveEnrolmentForKeys(
    serviceType: DirectDebitSource
  )(implicit request: AuthenticatedRequest[?]): Seq[Map[String, String]] = {

    val expectedHodServiceOpt = ChrisEnvelopeConstants.listHodServices.get(serviceType)

    // Only activated enrolments
    val active = request.enrolments.filter(_.isActivated)

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

  // TODO
  private def knownFactDataWithEnrolment(
    serviceType: DirectDebitSource
  )(implicit request: AuthenticatedRequest[?]): Seq[Map[String, String]] = {

    logger.debug(s"Retrieved enrolments: ${request.enrolments.map(_.key).mkString(", ")}")

    val expectedHodServiceOpt = ChrisEnvelopeConstants.listHodServices.get(serviceType)

    logger.info(s"Expected HOD service for [$serviceType] = ${expectedHodServiceOpt.getOrElse("not found")}")

    val activeEnrolments = request.enrolments.toSeq.filter(_.isActivated)

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

    logger.debug(s"SA keys originally present: ${grouped.keySet.intersect(saKeys.toSet)}")
    logger.debug(s"SA key retained for known fact: ${firstSaKeyOpt.getOrElse("none")}")

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

    reordered

  }

  def submitToChris(
    submission: ChrisSubmissionRequest
  )(implicit hc: HeaderCarrier, request: AuthenticatedRequest[?]): Future[SubmissionResult] = {
    val correlationId: String = UUID.randomUUID().toString.replace("-", "")

    val knownFactData = knownFactDataWithEnrolment(submission.serviceType)
    val keysData = getActiveEnrolmentForKeys(submission.serviceType)
    val envelopeDetails = EnvelopeDetails.details(submission, request, knownFactData, keysData, correlationId)

    validator.validate(envelopeDetails.build) match {
      case Failure(exception) => throw exception
      case Success(_) =>
        chrisConnector.submitEnvelope(envelopeDetails.build, correlationId) map {
          case submissionResult if submissionResult.status == SubmissionStatusSubmitted =>
            logger.debug(s"ChRIS submission successful for a correlationId = $correlationId")
            audit(envelopeDetails, correlationId)
            submissionResult
          case _ =>
            val message =
              s"ChRIS submission failed with " +
                s"SubmissionStatus = ${SubmissionStatusFatalError.name}, " +
                s"${request.sessionData}, " +
                s"correlationId = $correlationId"
            throw new RuntimeException(message)
        }
    }
  }

  private def audit(envelopeDetails: EnvelopeDetails, correlationId: String)(implicit
    hc: HeaderCarrier,
    request: AuthenticatedRequest[?]
  ): Future[String] = {
    auditService.sendEvent(envelopeDetails) map { result =>
      val message = s"{$result} for correlationId = $correlationId, ${request.sessionData}"
      result match {
        case AuditResult.Success => message
        case _ =>
          logger.error(message)
          throw new RuntimeException(message)
      }
    }
  }

}
