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

package actions

import play.api.mvc.{PlayBodyParsers, Request, Result}
import uk.gov.hmrc.http.SessionId
import uk.gov.hmrc.nationaldirectdebit.actions.AuthAction
import uk.gov.hmrc.nationaldirectdebit.models.requests.AuthenticatedRequest

import scala.concurrent.{ExecutionContext, Future}

class FakeAuthAction(
  bodyParsers: PlayBodyParsers,
  testCredId: String = "cred-123",
  testAffinity: String = "Individual",
  testNino: Option[String] = Some("AB123456C")
) extends AuthAction {

  override def parser = bodyParsers.default

  override def invokeBlock[A](request: Request[A], block: AuthenticatedRequest[A] => Future[Result]): Future[Result] =
    block(
      AuthenticatedRequest(
        request,
        internalId    = "internalId-123",
        sessionId     = SessionId("sessionId-123"),
        credId        = testCredId,
        affinityGroup = testAffinity,
        nino          = testNino
      )
    )

  override protected def executionContext: ExecutionContext =
    scala.concurrent.ExecutionContext.Implicits.global
}
