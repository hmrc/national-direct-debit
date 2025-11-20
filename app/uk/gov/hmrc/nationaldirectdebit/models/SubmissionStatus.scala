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

package uk.gov.hmrc.nationaldirectdebit.models

import play.api.libs.json.{Format, JsError, JsResult, JsString, JsSuccess, Json, OFormat}

// Base trait
sealed trait SubmissionStatus {
  def name: String
}

// Statuses
case object FATAL_ERROR extends SubmissionStatus { val name = "FATAL_ERROR" }
case object SUBMITTED   extends SubmissionStatus { val name = "SUBMITTED" }

object SubmissionStatus {
  implicit val format: Format[SubmissionStatus] = new Format[SubmissionStatus] {
    override def writes(o: SubmissionStatus) = JsString(o.name)

    override def reads(json: play.api.libs.json.JsValue): JsResult[SubmissionStatus] =
      json.validate[String].flatMap {
        case "FATAL_ERROR" => JsSuccess(FATAL_ERROR)
        case "SUBMITTED"   => JsSuccess(SUBMITTED)
        case other         => JsError(s"Unknown SubmissionStatus: $other")
      }
  }
}

// Submission result
final case class SubmissionResult(
  status: SubmissionStatus,
  rawXml: Option[String] = None, // optional, useful for stubs/logs
  meta: Option[String] = None // optional, can store additional info
)

object SubmissionResult {
  implicit val format: OFormat[SubmissionResult] = Json.format[SubmissionResult]
}
