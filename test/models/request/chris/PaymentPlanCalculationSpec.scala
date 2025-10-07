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
import java.time.LocalDate
import uk.gov.hmrc.nationaldirectdebit.models.requests.chris.PaymentPlanCalculation

class PaymentPlanCalculationSpec extends AnyWordSpec with Matchers {

  val jsonStr: String =
    s"""{
       |  "regularPaymentAmount": 100.50,
       |  "finalPaymentAmount": 50.25,
       |  "secondPaymentDate": "2025-09-15",
       |  "penultimatePaymentDate": "2025-09-20",
       |  "finalPaymentDate": "2025-09-25"
       |}""".stripMargin

  val model: PaymentPlanCalculation = PaymentPlanCalculation(
    regularPaymentAmount   = Some(BigDecimal(100.50)),
    finalPaymentAmount     = Some(BigDecimal(50.25)),
    secondPaymentDate      = Some(LocalDate.parse("2025-09-15")),
    penultimatePaymentDate = Some(LocalDate.parse("2025-09-20")),
    finalPaymentDate       = Some(LocalDate.parse("2025-09-25"))
  )

  val json: JsValue = Json.parse(jsonStr)

  "PaymentPlanCalculation" should {

    "read JSON correctly" in {
      Json.fromJson[PaymentPlanCalculation](json).get shouldBe model
    }

    "write JSON correctly" in {
      Json.toJson(model) shouldBe json
    }

  }

}
