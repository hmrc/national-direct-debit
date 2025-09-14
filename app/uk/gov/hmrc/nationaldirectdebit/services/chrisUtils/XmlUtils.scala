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

package uk.gov.hmrc.nationaldirectdebit.services.chrisUtils

import uk.gov.hmrc.nationaldirectdebit.models.requests.AuthenticatedRequest

import scala.xml.{Node, Text}

object XmlUtils {

  private def formatValue(value: String, request: AuthenticatedRequest[_]): String =
    if (value == "NINO") request.nino.getOrElse("").take(8)
    else value

  def formatKeys(
                  hodServices: Seq[Map[String, String]],
                  indent: String,
                  request: AuthenticatedRequest[_]
                ): Seq[scala.xml.Node] =
    hodServices.zipWithIndex.flatMap { case (serviceMap, idx) =>
      serviceMap.flatMap { case (k, v) =>
        val prefix = if (idx >= 0) "" else s"\n$indent"
        Seq(
          scala.xml.Text(prefix),
          <Key Type={k.trim}>{formatValue(v, request).trim}</Key>
        )
      }
    }


  def formatKnownFacts(
                        hodServices: Seq[Map[String, String]],
                        indent: String,
                        request: AuthenticatedRequest[_]
                      ): Seq[Node] =
    hodServices.flatMap { serviceMap =>
      serviceMap.toList.flatMap { case (k, v) =>
        Seq(
          <service>
            {k.trim}
          </service>,
          Text(s"\n$indent"),
          <value>
            {formatValue(v, request).trim}
          </value>
        )
      }
    }
}
