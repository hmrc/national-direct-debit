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

object ChrisEnvelopeConstants {
  val MessageClass = "HMRC-NDDS-DDI"
  val Qualifier    = "request"
  val Function     = "submit"
  val SenderSystem = "Portal"
  val ActionType_1 = 1
  val PPType_1 = 1
  val PPType_2 = 2
  val PPType_3 = 3
  val PPType_4 = 4
}
