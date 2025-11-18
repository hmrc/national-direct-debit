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

package models.request.chris

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.nationaldirectdebit.models.requests.chris.YearEndAndMonth

import java.time.LocalDate

class YearEndAndMonthSpec extends AnyWordSpec with Matchers {

  "YearEndAndMonth" should {

    "convert to LocalDate correctly for normal month" in {
      val yEndMonth = YearEndAndMonth(2025, 5)
      yEndMonth.toLocalDate shouldBe LocalDate.of(2025, 5, 1)
    }

    "convert to LocalDate correctly for special month value (13 -> December)" in {
      val yEndMonth = YearEndAndMonth(2025, 13)
      yEndMonth.toLocalDate shouldBe LocalDate.of(2025, 12, 1)
    }

    "display in correct format" in {
      val yEndMonth = YearEndAndMonth(2025, 5)
      yEndMonth.displayFormat shouldBe "2025 05"

      val yEndMonthSpecial = YearEndAndMonth(2025, 13)
      yEndMonthSpecial.displayFormat shouldBe "2025 13"
    }

    "read JSON correctly" in {
      val jsonStr = """{"year":2025,"month":5}"""
      val json: JsValue = Json.parse(jsonStr)
      Json.fromJson[YearEndAndMonth](json).get shouldBe YearEndAndMonth(2025, 5)
    }

    "write JSON correctly" in {
      val model = YearEndAndMonth(2025, 5)
      val expectedJsonStr = """{"year":2025,"month":5}"""
      Json.toJson(model).toString shouldBe expectedJsonStr
    }

    "create from LocalDate correctly" in {
      val date = LocalDate.of(2025, 7, 20)
      YearEndAndMonth.fromLocalDate(date) shouldBe YearEndAndMonth(2025, 7)
    }

  }

}
