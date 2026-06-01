package uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion

import java.util.UUID

data class IngestionContext(
  val prisonEmailAddress: String?,
  val prisonDocumentId: UUID?,
  val addressedPrison: String? = null,
  val warnings: List<String> = emptyList(),
)
