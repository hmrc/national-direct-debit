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

  case object Weekly      extends WithName("weekly") with PaymentsFrequency
  case object Monthly     extends WithName("monthly") with PaymentsFrequency
  case object Fortnightly extends WithName("fortNightly") with PaymentsFrequency
  case object FourWeekly  extends WithName("fourWeekly") with PaymentsFrequency
  case object Quarterly   extends WithName("quarterly") with PaymentsFrequency
  case object SixMonthly  extends WithName("sixMonthly") with PaymentsFrequency
  case object Annually    extends WithName("annually") with PaymentsFrequency

  val values: Seq[PaymentsFrequency] = Seq(
    Weekly,
    Monthly,
    Fortnightly,
    FourWeekly,
    Quarterly,
    SixMonthly,
    Annually
  )

  implicit val enumerable: Enumerable[PaymentsFrequency] =
    Enumerable(values.map(v => v.toString -> v)*)

  def auditName(oPaymentsFrequency: Option[PaymentsFrequency]): Option[String] =
    oPaymentsFrequency match {
      case Some(Weekly)      => Some("WEEKLY")
      case Some(Monthly)     => Some("MONTHLY")
      case Some(Fortnightly) => Some("FORTNIGHTLY")
      case Some(FourWeekly)  => Some("FOURWEEKLY")
      case Some(Quarterly)   => Some("QUARTERLY")
      case Some(SixMonthly)  => Some("SIXMONTHLY")
      case Some(Annually)    => Some("ANNUALLY")
      case _                 => None
    }

}
