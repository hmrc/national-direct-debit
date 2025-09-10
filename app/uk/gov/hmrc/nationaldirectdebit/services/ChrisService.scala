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
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.auth.core.authorise.EmptyPredicate
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
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


  def getEligibleHodServices()(implicit hc: HeaderCarrier): Future[Seq[String]] = {
    authConnector.authorise(EmptyPredicate, Retrievals.allEnrolments).flatMap { enrolments =>
      val activeEnrolments: Set[String] = enrolments.enrolments.collect {
        case e if e.isActivated => e.key
      }

      // map active enrolments to HOD services
      val candidateHodServices: Seq[String] = serviceToHod.collect {
        case (source, hod) if activeEnrolments.contains(source.toString) => hod
      }.toSeq

      // filter only DD-enabled ones (stubbed for now)
      Future.sequence(candidateHodServices.map { hod =>
        isDirectDebitEnabled(hod).map {
          case true  => Some(hod)
          case false => None
        }
      }).map(_.flatten)
    }
  }

  private def isDirectDebitEnabled(hod: String): Future[Boolean] = {
    // TODO: add connector check to real HOD/DD eligibility
    Future.successful(true)
  }

  private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")

  def submitToChris(request: ChrisSubmissionRequest, credId: String, affinityGroup: String)
                   (implicit hc: HeaderCarrier): Future[String] = {
    for {
      hodServices <- getEligibleHodServices()
      _ = logger.info(s"Eligible HOD services***************************: $hodServices")
      envelopeXml = buildEnvelopeXml(request, credId, affinityGroup)
      _ = logger.info(s"xml envelop ***************************: $envelopeXml")
      result <- chrisConnector.submitEnvelope(envelopeXml)
    } yield result
  }


  //Lookups for HOD service & known facts
  private val serviceToHod: Map[DirectDebitSource, String] = Map(
    DirectDebitSource.CT -> "COTA",
    DirectDebitSource.PAYE -> "PAYE",
    DirectDebitSource.SA -> "CESA",
    DirectDebitSource.TC -> "NTC",
    DirectDebitSource.VAT -> "VAT",
    DirectDebitSource.MGD -> "MGD",
    DirectDebitSource.NIC -> "NIDN",
    DirectDebitSource.OL -> "SAFE",
    DirectDebitSource.SDLT -> "SDLT"
  )

  private case class KnownFactConfig(factName: String, factType: String)

  private val hodToKnownFact: Map[String, KnownFactConfig] = Map(
    "COTA" -> KnownFactConfig("UTR", "CTUTR"),
    "PAYE" -> KnownFactConfig("employerReference", "EMPREF"),
    "CESA" -> KnownFactConfig("UTR", "UTR"),
    "NTC" -> KnownFactConfig("NINO", "NINO"),
    "MGD" -> KnownFactConfig("HMRCMGDRN", "MGDRN"),
    "TPSS" -> KnownFactConfig("PSAID", "PSAID"),
    "CIS" -> KnownFactConfig("employerReference", "EMPREF")
  )

  private def buildEnvelopeXml(request: ChrisSubmissionRequest, credId: String, affinityGroup: String): Elem = {
    val correlatingId = UUID.randomUUID().toString.replace("-", "")
    val receiptDate = LocalDateTime.now(ZoneOffset.UTC).format(dateTimeFormatter)
    val submissionDateTime = LocalDateTime.now(ZoneOffset.UTC).format(dateTimeFormatter)
    val periodEnd = calculatePeriodEnd()
    val senderType = if (affinityGroup == "agent") "Agent" else "Individual"

    val hodService = serviceToHod(request.serviceType)
    val knownFactCfg = hodToKnownFact.getOrElse(hodService, KnownFactConfig("UNKNOWN", "UNKNOWN"))

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
              <Key Type={knownFactCfg.factType}>
                {knownFactCfg.factName}
              </Key>
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
            </credentialID>
            <knownFact>
              <service>
                {hodService}
              </service>
              <value>
                {knownFactCfg.factName}
              </value>
            </knownFact>
            <directDebitInstruction>
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
            </directDebitInstruction>{buildPaymentPlanXml(request, hodService)}
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
