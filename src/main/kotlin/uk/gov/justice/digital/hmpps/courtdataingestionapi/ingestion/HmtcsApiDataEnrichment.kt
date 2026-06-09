package uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion

import java.util.UUID

data class HmtcsApiDataEnrichment(
  val courtName: String,
  val courtId: UUID,
  val hearingType: String,
)
