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
import uk.gov.hmrc.nationaldirectdebit.models.requests.chris.DirectDebitSource
import uk.gov.hmrc.nationaldirectdebit.services.ChrisEnvelopeConstants.enrolmentToHodService
import uk.gov.hmrc.nationaldirectdebit.services.chrisUtils.{ChrisEnvelopeBuilder, XmlValidator}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ChrisService @Inject() (chrisConnector: ChrisConnector, authConnector: AuthConnector, validator: XmlValidator)(implicit ec: ExecutionContext)
    extends Logging {

  private def getActiveEnrolmentForKeys(
    serviceType: DirectDebitSource
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[Map[String, String]]] = {

    authConnector.authorise(EmptyPredicate, Retrievals.allEnrolments).map { enrolments =>

      val expectedHodServiceOpt = ChrisEnvelopeConstants.listHodServices.get(serviceType)
      logger.info(s"Expected HOD service for [$serviceType] = ${expectedHodServiceOpt.getOrElse("not found")}")

      val activeEnrolments = enrolments.enrolments.toSeq.filter(_.isActivated)

      val grouped: Map[String, Seq[(String, String)]] =
        activeEnrolments
          .groupBy(_.key)
          .view
          .mapValues(_.flatMap(_.identifiers.map(i => i.key -> i.value)))
          .toMap

      val enrolmentMaps: Seq[Map[String, String]] =
        grouped.map { case (key, identifiers) =>
          val identifierName = identifiers.map(_._1).mkString("/")
          val identifierValue = identifiers
            .map {
              case ("NINO", v) => v.take(8)
              case (_, v)      => v
            }
            .mkString("/")

          Map(
            "enrolmentKey"    -> key,
            "identifierName"  -> identifierName,
            "identifierValue" -> identifierValue
          )
        }.toSeq

      val (matching, others) = expectedHodServiceOpt match {
        case Some(expectedHodService) =>
          enrolmentMaps.partition { enrolmentMap =>
            val enrolmentKey = enrolmentMap("enrolmentKey")
            enrolmentToHodService.get(enrolmentKey).contains(expectedHodService)
          }
        case None =>
          (Seq.empty, enrolmentMaps)
      }

      val reordered = matching ++ others
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
  )(implicit hc: HeaderCarrier): Future[String] = {

    for {
      knownFactData <- knownFactDataWithEnrolment(request.serviceType)
      keysData      <- getActiveEnrolmentForKeys(request.serviceType)
      envelopeXml = ChrisEnvelopeBuilder.build(request, credId, affinityGroup, knownFactData, keysData)

      validationResult = validator.validate(envelopeXml)

      result <- validationResult match {
                  case Success(_) =>
                    logger.info("ChRIS XML validation succeeded. Submitting envelope to ChRIS...")
                    chrisConnector.submitEnvelope(envelopeXml)

                  case Failure(e) =>
                    logger.error(s"ChRIS XML validation failed: ${e.getMessage}", e)
                    Future.failed(
                      new RuntimeException(s"ChRIS submission skipped due to invalid XML: ${e.getMessage}", e)
                    )
                }
    } yield result
  }

}
