package uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi

import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.UUID

data class HmctsPcr(
  val hearing: HmctsHearing,
  val offences: List<HmctsOffence>,
)

data class HmctsHearing(
  val id: String,
  val courtDetails: HmctsCourtDetails,
  val hearingDate: LocalDate,
  val hearingType: String,
  val jurisdiction: String,
  val nextHearing: HmctsNextHearing,
)

data class HmctsCourtDetails(
  val court: HmctsCourt,
  val ljaName: String,
)

data class HmctsCourt(
  val courtHouseCode: String,
  val courtHouseName: String,
  val courtHouseId: UUID,
)

data class HmctsNextHearing(
  val court: HmctsCourt,
  val dateTime: ZonedDateTime,
  val hearingId: String,
)

data class HmctsOffence(
  val code: String,
  val listingNumber: Int,
  val offenceLegislation: String,
  val pleaDate: LocalDate,
  val pleaValue: String,
  val results: List<HmctsResult>,
  val startDate: LocalDate,
  val endDate: LocalDate?,
  val title: String,
  val wording: String,
)

data class HmctsResult(
  val resultTexts: List<HmctsResultText>,
  val resultDescription: String,
)

data class HmctsResultText(
  val label: String,
  val value: String,
)
