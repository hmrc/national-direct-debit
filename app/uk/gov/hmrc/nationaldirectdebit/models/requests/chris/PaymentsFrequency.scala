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

sealed trait PaymentsFrequency

object PaymentsFrequency extends Enumerable.Implicits {

  case object Weekly  extends WithName("weekly") with PaymentsFrequency
  case object Monthly extends WithName("monthly") with PaymentsFrequency

  val values: Seq[PaymentsFrequency] = Seq(
    Weekly,
    Monthly
  )

  implicit val enumerable: Enumerable[PaymentsFrequency] =
    Enumerable(values.map(v => v.toString -> v)*)
}
