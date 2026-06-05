package uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.ingestion

import java.util.UUID

data class DuplicateResolutionOutcome(
  val duplicateOf: UUID?,
  val reason: String? = null,
)
