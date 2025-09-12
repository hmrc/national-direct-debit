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

import java.time.format.DateTimeFormatter
import java.time.LocalDate

object DateUtils {

  private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  def calculatePeriodEnd(now: LocalDate = LocalDate.now()): String = {
    val currentYear = now.getYear
    val taxYearStart = LocalDate.of(currentYear, 4, 6)

    val periodDate =
      if (!now.isBefore(taxYearStart)) taxYearStart.plusYears(1)
      else taxYearStart

    periodDate.format(formatter)
  }
}
