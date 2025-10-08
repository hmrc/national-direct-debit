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

import uk.gov.hmrc.nationaldirectdebit.models.requests.ChrisSubmissionRequest
import uk.gov.hmrc.nationaldirectdebit.models.requests.chris.{DirectDebitSource, PaymentPlanType, PaymentsFrequency}
import uk.gov.hmrc.nationaldirectdebit.services.ChrisEnvelopeConstants

import scala.xml.{Elem, Null}

object PaymentPlanBuilder {

  def build(request: ChrisSubmissionRequest, hodService: Option[String]): Elem = {
    request.serviceType match {
      case DirectDebitSource.SA if request.paymentPlanType == PaymentPlanType.BudgetPaymentPlan && request.amendPlan =>
        buildSaAmendPlan(request, hodService)
      case DirectDebitSource.TC if request.paymentPlanType == PaymentPlanType.TaxCreditRepaymentPlan =>
        buildTcPlan(request, hodService)
      case DirectDebitSource.MGD if request.paymentPlanType == PaymentPlanType.VariablePaymentPlan =>
        buildMgdPlan(request, hodService)
      case DirectDebitSource.SA if request.paymentPlanType == PaymentPlanType.BudgetPaymentPlan =>
        buildSaPlan(request, hodService)
      case _ if request.amendPlan =>
        buildAmendSinglePlan(request, hodService)
      case _ =>
        buildSinglePlan(request, hodService)
    }
  }

  private def frequencyCode(request: ChrisSubmissionRequest): String =
    request.paymentFrequency match {
      case Some(PaymentsFrequency.Weekly)  => "02"
      case Some(PaymentsFrequency.Monthly) => "05"
      case _                               => ""
    }

  private def buildTcPlan(request: ChrisSubmissionRequest, hodService: Option[String]): Elem =
    <paymentPlan>
      <actionType>{ChrisEnvelopeConstants.ActionType_1}</actionType>
      <pPType>{ChrisEnvelopeConstants.PPType_3}</pPType>
      <paymentReference>{request.paymentReference}</paymentReference>
      <hodService>{hodService.getOrElse("")}</hodService>
      <paymentCurrency>GBP</paymentCurrency>
      <scheduledPaymentAmount>{
      f"${request.calculation.flatMap(_.regularPaymentAmount).getOrElse(BigDecimal(0)).toDouble}%.2f"
    }</scheduledPaymentAmount>
      <scheduledPaymentStartDate>{request.planStartDate.map(_.enteredDate).getOrElse("")}</scheduledPaymentStartDate>
      <scheduledPaymentEndDate>{request.calculation.flatMap(_.finalPaymentDate).getOrElse("")}</scheduledPaymentEndDate>
      <scheduledPaymentFrequency>05</scheduledPaymentFrequency>
      <balancingPaymentAmount>{f"${request.calculation.flatMap(_.finalPaymentAmount).getOrElse(BigDecimal(0)).toDouble}%.2f"}</balancingPaymentAmount>
      <balancingPaymentDate>{request.calculation.flatMap(_.finalPaymentDate).getOrElse("")}</balancingPaymentDate>
      <totalLiability>{f"${request.totalAmountDue.getOrElse(BigDecimal(0)).toDouble}%.2f"}</totalLiability>
    </paymentPlan>

  private def buildMgdPlan(request: ChrisSubmissionRequest, hodService: Option[String]): Elem =
    <paymentPlan>
      <actionType>{ChrisEnvelopeConstants.ActionType_1}</actionType>
      <pPType>{ChrisEnvelopeConstants.PPType_4}</pPType>
      <paymentReference>{request.paymentReference}</paymentReference>
      <hodService>{hodService.getOrElse("")}</hodService>
      <paymentCurrency>GBP</paymentCurrency>
      <scheduledPaymentStartDate>{request.planStartDate.map(_.enteredDate).getOrElse("")}</scheduledPaymentStartDate>
    </paymentPlan>

  private def buildSaPlan(request: ChrisSubmissionRequest, hodService: Option[String]): Elem = {
    val freqCode = frequencyCode(request)
    <paymentPlan>
      <actionType>{ChrisEnvelopeConstants.ActionType_1}</actionType>
      <pPType>{ChrisEnvelopeConstants.PPType_2}</pPType>
      <paymentReference>{request.paymentReference}</paymentReference>
      <hodService>{hodService.getOrElse("")}</hodService>
      <paymentCurrency>GBP</paymentCurrency>
      <scheduledPaymentAmount>{f"${request.regularPaymentAmount.getOrElse(BigDecimal(0)).toDouble}%.2f"}</scheduledPaymentAmount>
      <scheduledPaymentStartDate>{request.planStartDate.map(_.enteredDate).getOrElse("")}</scheduledPaymentStartDate>
      <scheduledPaymentEndDate>{request.planEndDate.getOrElse("")}</scheduledPaymentEndDate>
      {if (freqCode.nonEmpty) <scheduledPaymentFrequency>{freqCode}</scheduledPaymentFrequency> else Null}
    </paymentPlan>
  }

  private def buildSaAmendPlan(request: ChrisSubmissionRequest, hodService: Option[String]): Elem = {
    val freqCode = frequencyCode(request)
    <paymentPlan>
      <actionType>{ChrisEnvelopeConstants.ActionType_2}</actionType>
      <pPType>{ChrisEnvelopeConstants.PPType_2}</pPType>
      <paymentReference>{request.paymentReference}</paymentReference>
      <corePPReferenceNo>{request.paymentPlanReferenceNumber.getOrElse("")}</corePPReferenceNo>
      <hodService>{hodService.getOrElse("")}</hodService>
      <paymentCurrency>GBP</paymentCurrency>
      <scheduledPaymentAmount>{f"${request.regularPaymentAmount.getOrElse(BigDecimal(0)).toDouble}%.2f"}</scheduledPaymentAmount>
      <scheduledPaymentStartDate>{request.planStartDate.map(_.enteredDate).getOrElse("")}</scheduledPaymentStartDate>
      <scheduledPaymentEndDate>{request.planEndDate.getOrElse("")}</scheduledPaymentEndDate>
      {if (freqCode.nonEmpty) <scheduledPaymentFrequency>{freqCode}</scheduledPaymentFrequency> else Null}
      <totalLiability>{request.totalAmountDue.getOrElse("")}</totalLiability>
    </paymentPlan>
  }

  private def buildSinglePlan(request: ChrisSubmissionRequest, hodService: Option[String]): Elem = {
    <paymentPlan>
      <actionType>{ChrisEnvelopeConstants.ActionType_1}</actionType>
      <pPType>{ChrisEnvelopeConstants.PPType_1}</pPType>
      <paymentReference>{request.paymentReference}</paymentReference>
      <hodService>{hodService.getOrElse("")}</hodService>
      <paymentCurrency>GBP</paymentCurrency>
      <scheduledPaymentAmount>{f"${request.paymentAmount.getOrElse(BigDecimal(0)).toDouble}%.2f"}</scheduledPaymentAmount>
      <scheduledPaymentStartDate>{request.paymentDate.map(_.enteredDate).getOrElse("")}</scheduledPaymentStartDate>
      <totalLiability>{f"${request.paymentAmount.getOrElse(BigDecimal(0)).toDouble}%.2f"}</totalLiability>
    </paymentPlan>
  }

  private def buildAmendSinglePlan(request: ChrisSubmissionRequest, hodService: Option[String]): Elem =
    val freqCode = frequencyCode(request)
    <paymentPlan>
      <actionType>{ChrisEnvelopeConstants.ActionType_2}</actionType>
      <pPType>{ChrisEnvelopeConstants.PPType_1}</pPType>
      <paymentReference>{request.paymentReference}</paymentReference>
      <corePPReferenceNo>{request.paymentPlanReferenceNumber.getOrElse("")}</corePPReferenceNo>
      <hodService>{hodService.getOrElse("")}</hodService>
      <paymentCurrency>GBP</paymentCurrency>
      <scheduledPaymentAmount>{f"${request.regularPaymentAmount.getOrElse(BigDecimal(0)).toDouble}%.2f"}</scheduledPaymentAmount>
      <scheduledPaymentStartDate>{request.planStartDate.map(_.enteredDate).getOrElse("")}</scheduledPaymentStartDate>
      <scheduledPaymentEndDate>{request.planEndDate.getOrElse("")}</scheduledPaymentEndDate>
      {if (freqCode.nonEmpty) <scheduledPaymentFrequency>{freqCode}</scheduledPaymentFrequency> else Null}
      <totalLiability>{f"${request.regularPaymentAmount.getOrElse(BigDecimal(0)).toDouble}%.2f"}</totalLiability>
    </paymentPlan>

}
