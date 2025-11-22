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

package services.chrisUtils

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.{Environment, Mode}
import uk.gov.hmrc.nationaldirectdebit.services.chrisUtils.SchemaLoader

import java.io.File
import javax.xml.validation.Schema

class SchemaLoaderSpec extends AnyWordSpec with Matchers {

  private val env =
    new Environment(new File("test/resources"), getClass.getClassLoader, Mode.Test)

  "SchemaLoader" should {

    "load a single valid XSD schema successfully" in {
      val schema: Schema = SchemaLoader.loadSchemas(Seq("test1.xsd"), env)

      schema                  must not be null
      schema.getClass.getName must include("Schema")
    }

    "load multiple valid XSD schemas successfully" in {
      val schema: Schema = SchemaLoader.loadSchemas(Seq("test1.xsd", "test2.xsd"), env)

      schema                  must not be null
      schema.getClass.getName must include("Schema")
    }

    "throw RuntimeException if XSD file is missing" in {
      val ex = intercept[RuntimeException] {
        SchemaLoader.loadSchemas(Seq("DOES_NOT_EXIST.xsd"), env)
      }
      ex.getMessage must include("Schema file not found")
    }
  }
}
