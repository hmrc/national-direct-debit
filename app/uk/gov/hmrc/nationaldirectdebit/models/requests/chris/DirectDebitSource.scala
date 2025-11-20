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

sealed trait DirectDebitSource

object DirectDebitSource extends Enumerable.Implicits {

  case object CT extends WithName("ct") with DirectDebitSource

  case object MGD extends WithName("mgd") with DirectDebitSource

  case object NIC extends WithName("nic") with DirectDebitSource

  case object OL extends WithName("otherLiability") with DirectDebitSource

  case object PAYE extends WithName("paye") with DirectDebitSource

  case object SA extends WithName("sa") with DirectDebitSource

  case object SDLT extends WithName("sdlt") with DirectDebitSource

  case object TC extends WithName("tc") with DirectDebitSource

  case object VAT extends WithName("vat") with DirectDebitSource

  val values: Seq[DirectDebitSource] = Seq(
    CT,
    MGD,
    NIC,
    OL,
    PAYE,
    SA,
    SDLT,
    TC,
    VAT
  )

  implicit val enumerable: Enumerable[DirectDebitSource] =
    Enumerable(values.map(v => v.toString -> v)*)

  val objectMap: Map[String, DirectDebitSource] = values.map(v => v.toString -> v).toMap

  def auditName(source: DirectDebitSource): String =
    source match {
      case CT   => "Corporation Tax"
      case MGD  => "Machine Games Duty"
      case NIC  => "National Insurance Contributions"
      case OL   => "Other liability"
      case PAYE => "Pay As You Earn"
      case SA   => "Self Assessment"
      case SDLT => "Stamp Duty Land Tax"
      case TC   => "Tax credits"
      case VAT  => "Value Added Tax"
    }
}
