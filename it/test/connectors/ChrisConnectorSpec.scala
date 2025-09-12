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

package connectors

import com.github.tomakehurst.wiremock.client.WireMock._
import itutil.ApplicationWithWiremock
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.nationaldirectdebit.connectors.ChrisConnector

import scala.xml.Elem

class ChrisConnectorSpec extends AnyWordSpec
  with Matchers
  with ScalaFutures
  with IntegrationPatience
  with ApplicationWithWiremock {

  val connector: ChrisConnector = app.injector.instanceOf[ChrisConnector]

  val testEnvelope: Elem =
    <Envelope>
      <Header>Test</Header>
      <Body>Dummy</Body>
    </Envelope>

  "ChrisConnector" should {
    "return confirmation on successful submission" in {
      stubFor(
        post(urlPathMatching("/submission/ChRIS/NDDS/Filing/async/HMRC-NDDS-DDI"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody("<Confirmation>Message received</Confirmation>")
          )
      )

      val result = connector.submitEnvelope(testEnvelope).futureValue
      result must include("Message received")
    }

    "throw an exception on 500 response" in {
      stubFor(
        post(urlPathMatching("/submission/ChRIS/NDDS/Filing/async/HMRC-NDDS-DDI"))
          .willReturn(
            aResponse()
              .withStatus(500)
              .withBody("error occurred")
          )
      )

      val ex = intercept[RuntimeException] {
        connector.submitEnvelope(testEnvelope).futureValue
      }
      ex.getMessage must include("ChRIS submission failed with status 500: error occurred")
    }

    "fail the future on network error" in {
      stubFor(
        post(urlPathMatching("/submission/ChRIS/NDDS/Filing/async/HMRC-NDDS-DDI"))
          .willReturn(
            aResponse()
              .withFault(com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER)
          )
      )

      intercept[Exception] {
        connector.submitEnvelope(testEnvelope).futureValue
      }.getMessage must include("The future returned an exception")
    }
  }
}
