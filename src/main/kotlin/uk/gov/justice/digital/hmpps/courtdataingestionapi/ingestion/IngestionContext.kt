package uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion

import java.util.UUID

data class IngestionContext(
  val prisonEmailAddress: String?,
  val prisonDocumentId: UUID?,

  val downloadedFileBytes: ByteArray? = null,
  val downloadedFileSha256: String? = null,

  val extractedText: String? = null,
  val extractedTextSha256: String? = null,

  val addressedPrison: String? = null,
  val addressedOrganisation: String? = null,
  val deliveryMappingId: UUID? = null,

  val duplicateOf: UUID? = null,

  val hmtcsApiDataEnrichment: HmtcsApiDataEnrichment? = null,

  val warnings: List<String> = emptyList(),
)
