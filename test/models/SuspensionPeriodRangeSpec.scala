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

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json
import uk.gov.hmrc.nationaldirectdebit.models.SuspensionPeriodRange

import java.time.LocalDate

class SuspensionPeriodRangeSpec extends AnyWordSpec with Matchers {

  "SuspensionPeriodRange" should {

    "serialize to JSON correctly" in {
      val model = SuspensionPeriodRange(
        startDate = LocalDate.of(2025, 10, 1),
        endDate   = LocalDate.of(2025, 10, 31)
      )

      val json = Json.toJson(model)

      (json \ "startDate").as[String] mustBe "2025-10-01"
      (json \ "endDate").as[String] mustBe "2025-10-31"
    }

    "deserialize from JSON correctly" in {
      val json = Json.parse(
        """
          |{
          |  "startDate": "2025-10-01",
          |  "endDate": "2025-10-31"
          |}
          |""".stripMargin
      )

      val result = json.as[SuspensionPeriodRange]

      result.startDate mustBe LocalDate.of(2025, 10, 1)
      result.endDate mustBe LocalDate.of(2025, 10, 31)
    }

    "support round-trip JSON serialization and deserialization" in {
      val model = SuspensionPeriodRange(
        startDate = LocalDate.of(2025, 5, 1),
        endDate   = LocalDate.of(2025, 5, 15)
      )

      val json = Json.toJson(model)
      val roundTrip = json.as[SuspensionPeriodRange]

      roundTrip mustBe model
    }
  }
}
