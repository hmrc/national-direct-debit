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
import uk.gov.hmrc.nationaldirectdebit.models.requests.chris.DirectDebitDetails

class DirectDebitDetailsSpec extends AnyWordSpec with Matchers {

  val jsonAsString: String =
    """{
      |  "directDebitReference":"DD123456",
      |  "setupDate":"2025-09-14",
      |  "sortCode":"207102",
      |  "accountNumber":"44311655",
      |  "paymentPlans":"PlanA"
      |}""".stripMargin

  val model: DirectDebitDetails = DirectDebitDetails(
    directDebitReference = "DD123456",
    setupDate            = "2025-09-14",
    sortCode             = "207102",
    accountNumber        = "44311655",
    paymentPlans         = "PlanA"
  )

  val json: JsValue = Json.parse(jsonAsString)

  "DirectDebitDetails" should {

    "read JSON correctly" in {
      // explicitly pass the format to avoid ambiguous implicits
      Json.fromJson[DirectDebitDetails](json)(DirectDebitDetails.format).get shouldBe model
    }

    "write JSON correctly" in {
      Json.toJson(model)(DirectDebitDetails.format).toString() shouldBe jsonAsString.replaceAll("\\s", "")
    }
  }
}
