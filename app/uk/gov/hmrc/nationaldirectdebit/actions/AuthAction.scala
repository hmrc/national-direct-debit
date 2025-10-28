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

package uk.gov.hmrc.nationaldirectdebit.actions

import play.api.Logging
import play.api.mvc.*
import play.api.mvc.Results.Unauthorized
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisationException, AuthorisedFunctions, Enrolment, Enrolments}
import uk.gov.hmrc.http.{HeaderCarrier, SessionId, UnauthorizedException}
import uk.gov.hmrc.nationaldirectdebit.models.requests.AuthenticatedRequest
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class DefaultAuthAction @Inject() (
  override val authConnector: AuthConnector,
  val parser: BodyParsers.Default
)(implicit val executionContext: ExecutionContext)
    extends AuthAction
    with AuthorisedFunctions
    with Logging:

  val acceptedEnrolments = Set(
    "IR-SA",
    "IR-SA-TRUST-ORG",
    "IR-SA-PART-ORG",
    "IR-CT",
    "IR-PAYE",
    "HMRC-CIS-ORG",
    "HMRC-MGD-ORG",
    "HMRC-ECL-ORG"
  )

  private def usingSupportedEnrolments(enrolments: Enrolments): Boolean = {
    enrolments.enrolments
      .exists {
        case e @ Enrolment("HMRC-PSA-ORG", _, _, _) if e.isActivated                        => true
        case e @ Enrolment(key, _, state, _) if e.isActivated || state == "NotYetActivated" => acceptedEnrolments(key)
        case _                                                                              => false
      }
  }

  override def invokeBlock[A](
    request: Request[A],
    block: AuthenticatedRequest[A] => Future[Result]
  ): Future[Result] =
    given hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

    val sessionId: SessionId = hc.sessionId
      .getOrElse(throw new UnauthorizedException("Unable to retrieve session ID from headers"))

    val retrievals = Retrievals.internalId and Retrievals.credentials and Retrievals.affinityGroup and Retrievals.nino and Retrievals.allEnrolments

    authorised()
      .retrieve(retrievals) { case maybeInternalId ~ maybeCreds ~ maybeAffinity ~ maybeNino ~ enrolments =>
        (maybeInternalId, maybeCreds, maybeAffinity, enrolments) match
          case (Some(internalId), Some(credentials), Some(affinity), userEnrolments) if usingSupportedEnrolments(userEnrolments) =>
            val credId = credentials.providerId
            val affinityName = affinity.toString
            block(
              AuthenticatedRequest(
                request,
                internalId,
                sessionId,
                credId,
                affinityName,
                nino = maybeNino
              )
            )

          case _ =>
            throw new UnauthorizedException("Unable to retrieve required auth values")
      }
      .recover { case _: AuthorisationException =>
        val error = "Failed to authorise request"
        logger.warn(error)
        Unauthorized(error)
      }

trait AuthAction extends ActionBuilder[AuthenticatedRequest, AnyContent] with ActionFunction[Request, AuthenticatedRequest]
