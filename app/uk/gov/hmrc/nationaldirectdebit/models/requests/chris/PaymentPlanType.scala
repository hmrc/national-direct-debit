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

sealed trait PaymentPlanType

object PaymentPlanType extends Enumerable.Implicits {

  case object SinglePayment extends WithName("singlePaymentPlan") with PaymentPlanType
  case object VariablePaymentPlan extends WithName("variablePaymentPlan") with PaymentPlanType
  case object BudgetPaymentPlan extends WithName("budgetPaymentPlan") with PaymentPlanType
  case object TaxCreditRepaymentPlan extends WithName("taxCreditRepaymentPlan") with PaymentPlanType

  // All values used for Enumerable mapping
  val values: Seq[PaymentPlanType] = Seq(
    SinglePayment, VariablePaymentPlan, BudgetPaymentPlan, TaxCreditRepaymentPlan
  )

  val values1: Seq[PaymentPlanType] = Seq(
    SinglePayment, VariablePaymentPlan
  )

  val values2: Seq[PaymentPlanType] = Seq(
    SinglePayment, BudgetPaymentPlan
  )

  val values3: Seq[PaymentPlanType] = Seq(
    SinglePayment, TaxCreditRepaymentPlan
  )
  

  implicit val enumerable: Enumerable[PaymentPlanType] =
    Enumerable(values.map(v => v.toString -> v): _*)

}
