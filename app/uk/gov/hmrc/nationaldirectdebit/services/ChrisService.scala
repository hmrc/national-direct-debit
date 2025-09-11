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

package uk.gov.hmrc.nationaldirectdebit.services

import play.api.Logging
import uk.gov.hmrc.auth.core.authorise.EmptyPredicate
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.{AuthConnector, Enrolment}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.nationaldirectdebit.connectors.ChrisConnector
import uk.gov.hmrc.nationaldirectdebit.models.requests.ChrisSubmissionRequest
import uk.gov.hmrc.nationaldirectdebit.models.requests.chris.{DirectDebitSource, PaymentPlanType, PaymentsFrequency}

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime, ZoneOffset}
import java.util.UUID
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.xml.Elem

class ChrisService @Inject()(chrisConnector: ChrisConnector,
                             authConnector: AuthConnector
                            )(implicit ec: ExecutionContext) extends Logging {


  private def getEligibleHodServices()
                                    (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[Map[String, String]]] = {
    authConnector.authorise(EmptyPredicate, Retrievals.allEnrolments).map { enrolments =>

      logger.info(s"Retrieved enrolments: ${enrolments.enrolments.map(_.key).mkString(", ")}")

      // Step 1: Filter active enrolments
      val activeEnrolments: Seq[Enrolment] = enrolments.enrolments.toSeq.filter(_.isActivated)

      // Step 2: Build Seq[Map(enrolmentKey -> identifierString)]
      val enrolmentMaps: Seq[Map[String, String]] = activeEnrolments.map { e =>
        val identifierString = e.identifiers.map(_.value).mkString("/") // join values like 222/CC222
        logger.info(s"Active enrolment: ${e.key} -> $identifierString")
        Map(e.key -> identifierString)
      }

      logger.info(s"Final enrolment maps: ${enrolmentMaps.mkString(", ")}")
      enrolmentMaps
    }
  }


  private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")

  def submitToChris(request: ChrisSubmissionRequest, credId: String, affinityGroup: String)
                   (implicit hc: HeaderCarrier): Future[String] = {
    for {
      hodServices <- getEligibleHodServices()
      _ = logger.info(s"Eligible HOD services***************************: $hodServices")
      envelopeXml = buildEnvelopeXml(request, credId, affinityGroup, hodServices)
      _ = logger.info(s"xml envelop ***************************: $envelopeXml")
      result <- chrisConnector.submitEnvelope(envelopeXml)
    } yield result
  }

  private def buildEnvelopeXml(request: ChrisSubmissionRequest, credId: String, affinityGroup: String, hodServices: Seq[Map[String, String]]): Elem = {
    val correlatingId = UUID.randomUUID().toString.replace("-", "")
    val receiptDate = LocalDateTime.now(ZoneOffset.UTC).format(dateTimeFormatter)
    val submissionDateTime = LocalDateTime.now(ZoneOffset.UTC).format(dateTimeFormatter)
    val periodEnd = calculatePeriodEnd()
    val senderType = if (affinityGroup == "agent") "Agent" else "Individual"
  

    <ChRISEnvelope xmlns="http://www.hmrc.gov.uk/ChRIS/Envelope/2">
      <EnvelopeVersion>2.0</EnvelopeVersion>
      <Header>
        <MessageClass>
          {ChrisEnvelopeConstants.MessageClass}
        </MessageClass>
        <Qualifier>
          {ChrisEnvelopeConstants.Qualifier}
        </Qualifier>
        <Function>
          {ChrisEnvelopeConstants.Function}
        </Function>
        <Sender>
          <System>
            {ChrisEnvelopeConstants.SenderSystem}
          </System>
          <CorrelatingID>
            {correlatingId}
          </CorrelatingID>
          <ReceiptDate>
            {receiptDate}
          </ReceiptDate>
        </Sender>
      </Header>
      <Body>
        <IRenvelope>// xmlns="" was removed from here
          <IRheader>
            <Keys>
              {hodServices.flatMap(_.map { case (hodKey, hodValue) =>
              scala.xml.Utility.trim(
                <Key Type={hodKey}>
                  {hodValue}
                </Key>
              )
            })}
            </Keys>
            <PeriodEnd>
              {periodEnd}
            </PeriodEnd>
            <Sender>
              {senderType}
            </Sender>
          </IRheader>
          <dDIPPDetails>
            <submissionDateTime>
              {submissionDateTime}
            </submissionDateTime>
            <credentialID>
              {credId}
            </credentialID>{hodServices.flatMap(_.map { case (hodKey, hodValue) =>
            scala.xml.Utility.trim(
              <knownFact>
                <service>
                  {hodKey}
                </service>
                <value>
                  {hodValue}
                </value>
              </knownFact>
            )
          })}<directDebitInstruction>
            <actionType>
              {ChrisEnvelopeConstants.ActionType_1}
            </actionType>
            <ddiReferenceNo>
              {request.ddiReferenceNo}
            </ddiReferenceNo>
            <bankSortCode>
              {request.yourBankDetailsWithAuddisStatus.sortCode}
            </bankSortCode>
            <bankAccountNo>
              {request.yourBankDetailsWithAuddisStatus.accountNumber}
            </bankAccountNo>
            <bankAccountName>
              {request.bankName}
            </bankAccountName>{if (request.yourBankDetailsWithAuddisStatus.auddisStatus) <paperAuddisFlag>01</paperAuddisFlag> else scala.xml.Null}
          </directDebitInstruction>{buildPaymentPlanXml(request, "hodService")}
          </dDIPPDetails>
        </IRenvelope>
      </Body>
    </ChRISEnvelope>
  }

  private def calculatePeriodEnd(): String = {
    val now = LocalDate.now()
    val currentYear = now.getYear
    val taxYearStart = LocalDate.of(currentYear, 4, 6)
    val periodDate = if (now.isBefore(taxYearStart)) taxYearStart else taxYearStart.plusYears(1)
    periodDate.toString
  }

  private def buildPaymentPlanXml(request: ChrisSubmissionRequest, hodService: String): Elem =
    val frequencyCode = request.paymentFrequency match {
      case Some(PaymentsFrequency.Weekly) => "02"
      case Some(PaymentsFrequency.Monthly) => "05"
      case _ => ""
    }

    request.serviceType match {
      case DirectDebitSource.TC if request.paymentPlanType == PaymentPlanType.TaxCreditRepaymentPlan =>
        <paymentPlan>
          <actionType>
            {ChrisEnvelopeConstants.ActionType_1}
          </actionType>
          <pPType>
            {ChrisEnvelopeConstants.PPType_3}
          </pPType>
          <paymentReference>
            {request.paymentReference.getOrElse("")}
          </paymentReference>
          <hodService>
            {hodService}
          </hodService>
          <paymentCurrency>GBP</paymentCurrency>
          <scheduledPaymentAmount>
            {request.calculation.flatMap(a => a.regularPaymentAmount).getOrElse("")}
          </scheduledPaymentAmount>
          <scheduledPaymentStartDate>
            {request.planStartDate.map(_.enteredDate).getOrElse("")}
          </scheduledPaymentStartDate>
          <scheduledPaymentEndDate>
            {request.calculation.flatMap(a => a.finalPaymentDate).getOrElse("")}
          </scheduledPaymentEndDate>
          // check plane end date is available in this journey
          <scheduledPaymentFrequency>05</scheduledPaymentFrequency>
          <balancingPaymentAmount>
            {request.calculation.flatMap(a => a.finalPaymentAmount).getOrElse(BigDecimal(0))}
          </balancingPaymentAmount>
          <balancingPaymentDate>
            {request.calculation.flatMap(a => a.finalPaymentDate).getOrElse("")}
          </balancingPaymentDate>
          <totalLiability>
            {request.totalAmountDue.getOrElse(BigDecimal(0))}
          </totalLiability>
        </paymentPlan>

      case DirectDebitSource.MGD if request.paymentPlanType == PaymentPlanType.VariablePaymentPlan =>
        <paymentPlan>
          <actionType>
            {ChrisEnvelopeConstants.ActionType_1}
          </actionType>
          <pPType>
            {ChrisEnvelopeConstants.PPType_4}
          </pPType>
          <paymentReference>
            {request.paymentReference.getOrElse("")}
          </paymentReference>
          <hodService>
            {hodService}
          </hodService>
          <paymentCurrency>GBP</paymentCurrency>
          <scheduledPaymentStartDate>
            {request.planStartDate.map(_.enteredDate).getOrElse("")}
          </scheduledPaymentStartDate>
        </paymentPlan>

      case DirectDebitSource.SA if request.paymentPlanType == PaymentPlanType.BudgetPaymentPlan =>
        <paymentPlan>
          <actionType>
            {ChrisEnvelopeConstants.ActionType_1}
          </actionType>
          <pPType>
            {ChrisEnvelopeConstants.PPType_2}
          </pPType>
          <paymentReference>
            {request.paymentReference.getOrElse("")}
          </paymentReference>
          <hodService>
            {hodService}
          </hodService>
          <paymentCurrency>GBP</paymentCurrency>
          <scheduledPaymentAmount>
            {request.regularPaymentAmount.getOrElse(BigDecimal(0))}
          </scheduledPaymentAmount>
          <scheduledPaymentStartDate>
            {request.planStartDate.map(_.enteredDate).getOrElse("")}
          </scheduledPaymentStartDate>
          <scheduledPaymentEndDate>
            {request.planEndDate.getOrElse("")}
          </scheduledPaymentEndDate>{if (frequencyCode.nonEmpty) <scheduledPaymentFrequency>
          {frequencyCode}
        </scheduledPaymentFrequency> else scala.xml.Null}
        </paymentPlan>

      case _ => // single payment
        <paymentPlan>
          <actionType>
            {ChrisEnvelopeConstants.ActionType_1}
          </actionType>
          <pPType>
            {ChrisEnvelopeConstants.PPType_1}
          </pPType>
          <paymentReference>
            {request.paymentReference.getOrElse("")}
          </paymentReference>
          <hodService>
            {hodService}
          </hodService>
          <paymentCurrency>GBP</paymentCurrency>
          <scheduledPaymentAmount>
            {request.paymentAmount.getOrElse(BigDecimal(0))}
          </scheduledPaymentAmount>
          <scheduledPaymentStartDate>
            {request.paymentDate.map(psd => psd.enteredDate).getOrElse("")}
          </scheduledPaymentStartDate>
          <totalLiability>
            {request.paymentAmount.getOrElse(BigDecimal(0))}
          </totalLiability>
        </paymentPlan>
    }
}
