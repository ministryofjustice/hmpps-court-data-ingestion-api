package uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api

import java.time.LocalDateTime

data class CourtHearing(
  val courtName: String,
  val hearingType: String,
  val hearingDate: LocalDateTime,
)
