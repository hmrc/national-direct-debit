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
import uk.gov.hmrc.nationaldirectdebit.models.requests.chris.{Bank, BankAddress, Country}

class BankSpec extends AnyWordSpec with Matchers {

  val countryJsonStr: String = """{"name":"United Kingdom"}"""
  val countryModel: Country = Country(name = "United Kingdom")
  val countryJson: JsValue = Json.parse(countryJsonStr)

  val bankAddressJsonStr: String =
    """{
      |  "lines":["1 High Street","Floor 2"],
      |  "town":"London",
      |  "country":{"name":"United Kingdom"},
      |  "postCode":"SW1A 1AA"
      |}""".stripMargin
  val bankAddressModel: BankAddress = BankAddress(
    lines    = Seq("1 High Street", "Floor 2"),
    town     = "London",
    country  = countryModel,
    postCode = "SW1A 1AA"
  )
  val bankAddressJson: JsValue = Json.parse(bankAddressJsonStr)

  val bankJsonStr: String =
    """{
      |  "bankName":"Barclays Bank UK PLC",
      |  "address":{
      |    "lines":["1 High Street","Floor 2"],
      |    "town":"London",
      |    "country":{"name":"United Kingdom"},
      |    "postCode":"SW1A 1AA"
      |  }
      |}""".stripMargin
  val bankModel: Bank = Bank(
    bankName = "Barclays Bank UK PLC",
    address  = bankAddressModel
  )
  val bankJson: JsValue = Json.parse(bankJsonStr)

  "Country" should {
    "read JSON correctly" in {
      Json.fromJson[Country](countryJson).get shouldBe countryModel
    }
    "write JSON correctly" in {
      Json.toJson(countryModel) shouldBe countryJson
    }
  }

  "BankAddress" should {
    "read JSON correctly" in {
      Json.fromJson[BankAddress](bankAddressJson).get shouldBe bankAddressModel
    }
    "write JSON correctly" in {
      Json.toJson(bankAddressModel) shouldBe bankAddressJson
    }
  }

  "Bank" should {
    "read JSON correctly" in {
      Json.fromJson[Bank](bankJson).get shouldBe bankModel
    }
    "write JSON correctly" in {
      Json.toJson(bankModel) shouldBe bankJson
    }
  }

}
