package uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion

import java.time.LocalDateTime
import java.util.UUID

data class HmctsApiDataEnrichment(
  val courtName: String,
  val courtId: UUID,
  val hearingType: String,
  val hearingDate: LocalDateTime,
)
