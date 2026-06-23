package uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api

import java.time.LocalDateTime

data class DocumentCourtHearing(
  val courtName: String,
  val hearingType: String,
  val hearingDate: LocalDateTime,
)
