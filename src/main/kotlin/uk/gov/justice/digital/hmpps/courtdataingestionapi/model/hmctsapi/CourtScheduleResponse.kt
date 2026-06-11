package uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi

import java.time.LocalDateTime
import java.util.UUID

data class CourtScheduleResponse(
  val courtSchedule: List<CourtSchedule>,
)

data class CourtSchedule(
  val hearings: List<Hearing>,
)

data class Hearing(
  val courtSittings: List<CourtSitting>,
  val hearingId: UUID,
  val hearingType: String,
  val hearingDescription: String,
)

data class CourtSitting(
  val courtHouse: UUID,
  val sittingStart: LocalDateTime,
)
