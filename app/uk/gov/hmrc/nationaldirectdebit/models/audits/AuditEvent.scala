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

package uk.gov.hmrc.nationaldirectdebit.models.audits

import play.api.libs.json.*
import play.api.libs.functional.syntax.*

import java.time.LocalDate

final case class CommonAuditFields(
  auditType: String,
  correlatingId: String,
  credentialId: String,
  directDebitReference: String,
  submissionDateTime: String,
  paymentPlanService: String,
  paymentPlanType: String,
  paymentReference: String
)

sealed trait AuditEvent {
  def common: CommonAuditFields
}

final case class NewDirectDebitAuditEvent(
  common: CommonAuditFields,
  bankAccountType: String,
  bankAccount: BankAccount,
  bankAuddisEnabled: Boolean,
  paymentCurrency: Option[String],
  paymentPlanDetails: Option[PaymentPlanDetails]
) extends AuditEvent

final case class AddPaymentPlanAuditEvent(
  common: CommonAuditFields,
  paymentCurrency: Option[String],
  paymentPlanDetails: Option[PaymentPlanDetails]
) extends AuditEvent

final case class AmendPaymentPlanAuditEvent(
  common: CommonAuditFields,
  paymentCurrency: Option[String],
  paymentPlanDetails: Option[PaymentPlanDetails]
) extends AuditEvent

final case class SuspendPaymentPlanAuditEvent(
  common: CommonAuditFields,
  suspensionStartDate: Option[LocalDate],
  suspensionEndDate: Option[LocalDate]
) extends AuditEvent

final case class AmendPaymentPlanSuspensionAuditEvent(
  common: CommonAuditFields,
  suspensionStartDate: Option[LocalDate],
  suspensionEndDate: Option[LocalDate]
) extends AuditEvent

final case class RemovePaymentPlanSuspensionAuditEvent(
  common: CommonAuditFields,
  isSuspensionCancelled: Boolean
) extends AuditEvent

final case class CancelPaymentPlanAuditEvent(
  common: CommonAuditFields,
  isPaymentPlanCancelled: Boolean
) extends AuditEvent

object AuditEvent {

  implicit val commonReads: Reads[CommonAuditFields] = (
    (__ \ "auditType").read[String] and
      (__ \ "correlatingId").read[String] and
      (__ \ "credentialId").read[String] and
      (__ \ "directDebitReference").read[String] and
      (__ \ "submissionDateTime").read[String] and
      (__ \ "paymentPlanService").read[String] and
      (__ \ "paymentPlanType").read[String] and
      (__ \ "paymentReference").read[String]
  )(CommonAuditFields.apply)

  implicit val commonWrites: OWrites[CommonAuditFields] = OWrites { o =>
    Json.obj(
      "correlatingId"        -> o.correlatingId,
      "credentialId"         -> o.credentialId,
      "directDebitReference" -> o.directDebitReference,
      "submissionDateTime"   -> o.submissionDateTime,
      "paymentPlanService"   -> o.paymentPlanService,
      "paymentPlanType"      -> o.paymentPlanType,
      "paymentReference"     -> o.paymentReference
    )
  }

  implicit val commonFormat: OFormat[CommonAuditFields] =
    OFormat(commonReads, commonWrites)

  implicit val newDDFormat: OFormat[NewDirectDebitAuditEvent] = OFormat(
    Reads[NewDirectDebitAuditEvent] { json =>
      for {
        common             <- commonFormat.reads(json)
        bankAccountType    <- (json \ "bankAccountType").validate[String]
        bankAccount        <- (json \ "bankAccount").validate[BankAccount]
        bankAuddisEnabled  <- (json \ "bankAuddisEnabled").validate[Boolean]
        paymentCurrency    <- (json \ "paymentCurrency").validateOpt[String]
        paymentPlanDetails <- (json \ "paymentPlanDetails").validateOpt[PaymentPlanDetails]
      } yield NewDirectDebitAuditEvent(common, bankAccountType, bankAccount, bankAuddisEnabled, paymentCurrency, paymentPlanDetails)
    },
    OWrites[NewDirectDebitAuditEvent] { event =>
      Json.toJsObject(event.common) ++
        Json.obj(
          "bankAccountType"    -> event.bankAccountType,
          "bankAccount"        -> event.bankAccount,
          "bankAuddisEnabled"  -> event.bankAuddisEnabled,
          "paymentCurrency"    -> event.paymentCurrency,
          "paymentPlanDetails" -> event.paymentPlanDetails
        )
    }
  )

  implicit val addPPFormat: OFormat[AddPaymentPlanAuditEvent] = OFormat(
    Reads[AddPaymentPlanAuditEvent] { json =>
      for {
        common             <- commonFormat.reads(json)
        paymentCurrency    <- (json \ "paymentCurrency").validateOpt[String]
        paymentPlanDetails <- (json \ "paymentPlanDetails").validateOpt[PaymentPlanDetails]
      } yield AddPaymentPlanAuditEvent(common, paymentCurrency, paymentPlanDetails)
    },
    OWrites[AddPaymentPlanAuditEvent] { event =>
      Json.toJsObject(event.common) ++
        Json.obj(
          "paymentCurrency"    -> event.paymentCurrency,
          "paymentPlanDetails" -> event.paymentPlanDetails
        )
    }
  )

  implicit val amendPPFormat: OFormat[AmendPaymentPlanAuditEvent] = OFormat(
    Reads[AmendPaymentPlanAuditEvent] { json =>
      for {
        common             <- commonFormat.reads(json)
        paymentCurrency    <- (json \ "paymentCurrency").validateOpt[String]
        paymentPlanDetails <- (json \ "paymentPlanDetails").validateOpt[PaymentPlanDetails]
      } yield AmendPaymentPlanAuditEvent(common, paymentCurrency, paymentPlanDetails)
    },
    OWrites[AmendPaymentPlanAuditEvent] { event =>
      Json.toJsObject(event.common) ++
        Json.obj(
          "paymentCurrency"    -> event.paymentCurrency,
          "paymentPlanDetails" -> event.paymentPlanDetails
        )
    }
  )

  implicit val suspendPPFormat: OFormat[SuspendPaymentPlanAuditEvent] = OFormat(
    Reads[SuspendPaymentPlanAuditEvent] { json =>
      for {
        common              <- commonFormat.reads(json)
        suspensionStartDate <- (json \ "suspensionStartDate").validateOpt[LocalDate]
        suspensionEndDate   <- (json \ "suspensionEndDate").validateOpt[LocalDate]
      } yield SuspendPaymentPlanAuditEvent(common, suspensionStartDate, suspensionEndDate)
    },
    OWrites[SuspendPaymentPlanAuditEvent] { event =>
      Json.toJsObject(event.common) ++
        Json.obj(
          "suspensionStartDate" -> event.suspensionStartDate,
          "suspensionEndDate"   -> event.suspensionEndDate
        )
    }
  )

  implicit val amendPPSuspensionFormat: OFormat[AmendPaymentPlanSuspensionAuditEvent] = OFormat(
    Reads[AmendPaymentPlanSuspensionAuditEvent] { json =>
      for {
        common              <- commonFormat.reads(json)
        suspensionStartDate <- (json \ "suspensionStartDate").validateOpt[LocalDate]
        suspensionEndDate   <- (json \ "suspensionEndDate").validateOpt[LocalDate]
      } yield AmendPaymentPlanSuspensionAuditEvent(common, suspensionStartDate, suspensionEndDate)
    },
    OWrites[AmendPaymentPlanSuspensionAuditEvent] { event =>
      Json.toJsObject(event.common) ++
        Json.obj(
          "suspensionStartDate" -> event.suspensionStartDate,
          "suspensionEndDate"   -> event.suspensionEndDate
        )
    }
  )

  implicit val removePPSuspensionFormat: OFormat[RemovePaymentPlanSuspensionAuditEvent] = OFormat(
    Reads[RemovePaymentPlanSuspensionAuditEvent] { json =>
      for {
        common                <- commonFormat.reads(json)
        isSuspensionCancelled <- (json \ "isSuspensionCancelled").validate[Boolean]
      } yield RemovePaymentPlanSuspensionAuditEvent(common, isSuspensionCancelled)
    },
    OWrites[RemovePaymentPlanSuspensionAuditEvent] { event =>
      Json.toJsObject(event.common) ++
        Json.obj(
          "isSuspensionCancelled" -> event.isSuspensionCancelled
        )
    }
  )

  implicit val cancelPPFormat: OFormat[CancelPaymentPlanAuditEvent] = OFormat(
    Reads[CancelPaymentPlanAuditEvent] { json =>
      for {
        common                 <- commonFormat.reads(json)
        isPaymentPlanCancelled <- (json \ "isPaymentPlanCancelled").validate[Boolean]
      } yield CancelPaymentPlanAuditEvent(common, isPaymentPlanCancelled)
    },
    OWrites[CancelPaymentPlanAuditEvent] { event =>
      Json.toJsObject(event.common) ++
        Json.obj(
          "isPaymentPlanCancelled" -> event.isPaymentPlanCancelled
        )
    }
  )

  private val registry: Map[String, OFormat[? <: AuditEvent]] = Map(
    "NewDirectDebit"              -> newDDFormat,
    "AddPaymentPlan"              -> addPPFormat,
    "AmendPaymentPlan"            -> amendPPFormat,
    "SuspendPaymentPlan"          -> suspendPPFormat,
    "AmendPaymentPlanSuspension"  -> amendPPSuspensionFormat,
    "RemovePaymentPlanSuspension" -> removePPSuspensionFormat,
    "CancelPaymentPlan"           -> cancelPPFormat
  )

  implicit val auditEventFormat: OFormat[AuditEvent] = new OFormat[AuditEvent] {
    def reads(json: JsValue): JsResult[AuditEvent] = {
      (json \ "auditType").validate[String].flatMap { auditType =>
        registry.get(auditType) match {
          case Some(format) =>
            format.reads(json).asInstanceOf[JsResult[AuditEvent]]
          case None =>
            JsError(s"Unknown auditType: $auditType")
        }
      }
    }

    def writes(event: AuditEvent): JsObject = {
      val auditType = event.common.auditType
      registry.get(auditType) match {
        case Some(format) =>
          format.asInstanceOf[OFormat[AuditEvent]].writes(event)
        case None =>
          throw new IllegalArgumentException(s"No format registered for auditType: $auditType")
      }
    }
  }

}
