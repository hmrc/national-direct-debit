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

import com.github.tomakehurst.wiremock.client.WireMock.*
import itutil.{ApplicationWithWiremock, WireMockConstants}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.nationaldirectdebit.connectors.ChrisConnector
import uk.gov.hmrc.nationaldirectdebit.models.SUBMITTED

import scala.xml.Elem

class ChrisConnectorSpec extends AnyWordSpec with Matchers with ScalaFutures with IntegrationPatience with ApplicationWithWiremock {
  implicit val hc: HeaderCarrier = HeaderCarrier()

  override lazy val extraConfig: Map[String, Any] = super.extraConfig ++ Map(
    "microservice.services.chris.host" -> WireMockConstants.stubHost,
    "microservice.services.chris.port" -> WireMockConstants.stubPort
  )

  val connector: ChrisConnector = app.injector.instanceOf[ChrisConnector]
  val CorrelatingID = "668102531dd2491f81811a90dac00a33"
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

      val result = connector.submitEnvelope(testEnvelope, CorrelatingID).futureValue
      result.status mustBe SUBMITTED
      result.rawXml.get must include("Message received")
    }

    "return FATAL_ERROR on 500 response" in {
      stubFor(
        post(urlPathMatching("/submission/ChRIS/NDDS/Filing/async/HMRC-NDDS-DDI"))
          .willReturn(
            aResponse()
              .withStatus(500)
              .withBody("error occurred")
          )
      )

      val result = connector.submitEnvelope(testEnvelope, CorrelatingID).futureValue
      result.status mustBe uk.gov.hmrc.nationaldirectdebit.models.FATAL_ERROR
      result.rawXml.get must include("error occurred")
    }

    "return FATAL_ERROR on network error" in {
      stubFor(
        post(urlPathMatching("/submission/ChRIS/NDDS/Filing/async/HMRC-NDDS-DDI"))
          .willReturn(
            aResponse()
              .withFault(com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER)
          )
      )

      val result = connector.submitEnvelope(testEnvelope, CorrelatingID).futureValue
      result.status mustBe uk.gov.hmrc.nationaldirectdebit.models.FATAL_ERROR
      result.rawXml.get must include("<connection-error/>")
    }

  }
}
