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

import scala.xml.{Node, Text}

object XmlUtils {

  def formatKeys(
    enrolments: Seq[Map[String, String]]
  ): Seq[scala.xml.Node] =
    enrolments.flatMap { serviceMap =>
      (for {
        enrolmentKey <- serviceMap.get("enrolmentKey")
        idName       <- serviceMap.get("identifierName")
        idValue      <- serviceMap.get("identifierValue")
      } yield {
        val valueToUse =
          if (idName.equalsIgnoreCase("NINO"))
            idValue.take(8)
          else
            idValue.trim

        Seq(
          <Key Type={idName.trim}>{valueToUse}</Key>
        )
      }).getOrElse(Seq.empty)
    }

  def formatKnownFacts(hodServices: Seq[Map[String, String]]): Seq[scala.xml.Node] =
    hodServices.flatMap { serviceMap =>
      for {
        hodService <- serviceMap.get("service")
        idValues   <- serviceMap.get("identifierValue")
      } yield {
        <knownFact>
          <service>{hodService.trim}</service>
          <value>{idValues}</value>
        </knownFact>
      }
    }
}
