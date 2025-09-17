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
import uk.gov.hmrc.nationaldirectdebit.models.requests.chris.YourBankDetails

class YourBankDetailsSpec extends AnyWordSpec with Matchers {

  val jsonStr: String =
    """{"accountHolderName":"John Doe","sortCode":"12 34 56","accountNumber":"12345678"}"""
  val model: YourBankDetails = YourBankDetails(
    accountHolderName = "John Doe",
    sortCode = "12 34 56",
    accountNumber = "12345678"
  )
  val json: JsValue = Json.parse(jsonStr)

  "YourBankDetails" should {

    "read JSON correctly" in {
      Json.fromJson[YourBankDetails](json).get shouldBe model
    }

    "write JSON correctly" in {
      Json.toJson(model).toString shouldBe jsonStr
    }

    "compute sortCodeNoSpaces correctly" in {
      model.sortCodeNoSpaces shouldBe "123456"
    }

  }

}
