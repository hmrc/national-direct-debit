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

package uk.gov.hmrc.nationaldirectdebit.models.requests.chris

sealed trait PersonalOrBusinessAccount

object PersonalOrBusinessAccount extends Enumerable.Implicits {

  case object Personal extends WithName("personal") with PersonalOrBusinessAccount

  case object Business extends WithName("business") with PersonalOrBusinessAccount

  val values: Seq[PersonalOrBusinessAccount] = Seq(
    Personal, Business
  )

  implicit val enumerable: Enumerable[PersonalOrBusinessAccount] =
    Enumerable(values.map(v => v.toString -> v): _*)
}
