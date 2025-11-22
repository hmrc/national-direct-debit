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

package models
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.libs.json.*
import uk.gov.hmrc.nationaldirectdebit.models.{FATAL_ERROR, SUBMITTED, SubmissionResult, SubmissionStatus}

class SubmissionModelsSpec extends AnyFreeSpec with Matchers {

  "SubmissionStatus JSON serialization" - {

    "serialize SUBMITTED to JSON string" in {
      Json.toJson(SUBMITTED: SubmissionStatus) mustBe JsString("SUBMITTED")
    }

    "serialize FATAL_ERROR to JSON string" in {
      Json.toJson(FATAL_ERROR: SubmissionStatus) mustBe JsString("FATAL_ERROR")
    }

    "deserialize SUBMITTED from JSON string" in {
      Json.fromJson[SubmissionStatus](JsString("SUBMITTED")).get mustBe SUBMITTED
    }

    "deserialize FATAL_ERROR from JSON string" in {
      Json.fromJson[SubmissionStatus](JsString("FATAL_ERROR")).get mustBe FATAL_ERROR
    }

    "fail to deserialize unknown status" in {
      val result = Json.fromJson[SubmissionStatus](JsString("UNKNOWN"))
      result.isError mustBe true
      val errors = result.asEither.swap.getOrElse(Seq.empty)
      errors.head._2.head.message must include("Unknown SubmissionStatus")
    }

  }

  "SubmissionResult JSON serialization" - {

    val result = SubmissionResult(
      status = SUBMITTED,
      rawXml = Some("<xml>test</xml>"),
      meta   = Some("meta-info")
    )

    "serialize SubmissionResult to JSON" in {
      val json = Json.toJson(result)
      (json \ "status").as[String] mustBe "SUBMITTED"
      (json \ "rawXml").as[String] mustBe "<xml>test</xml>"
      (json \ "meta").as[String] mustBe "meta-info"
    }

    "deserialize SubmissionResult from JSON" in {
      val json = Json.obj(
        "status" -> "FATAL_ERROR",
        "rawXml" -> "<xml>fail</xml>",
        "meta"   -> "some-meta"
      )
      val parsed = Json.fromJson[SubmissionResult](json).get
      parsed.status mustBe FATAL_ERROR
      parsed.rawXml mustBe Some("<xml>fail</xml>")
      parsed.meta mustBe Some("some-meta")
    }

    "handle optional fields when missing" in {
      val json = Json.obj("status" -> "SUBMITTED")
      val parsed = Json.fromJson[SubmissionResult](json).get
      parsed.status mustBe SUBMITTED
      parsed.rawXml mustBe None
      parsed.meta mustBe None
    }
  }
}
