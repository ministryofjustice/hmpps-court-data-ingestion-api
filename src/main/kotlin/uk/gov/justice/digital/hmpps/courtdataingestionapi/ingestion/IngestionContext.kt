package uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion

import java.util.UUID

data class IngestionContext(
  val prisonEmailAddress: String?,
  val prisonDocumentId: UUID?,
  val hearingId: UUID?,
  val caseReferences: List<String>?,

  // Step 1–2: raw file + hash
  val downloadedFileBytes: ByteArray? = null,
  val downloadedFileSha256: String? = null,

  // Step 3–4: extracted text + hash
  val extractedText: String? = null,
  val extractedTextSha256: String? = null,

  // Step 5: destination classification
  val addressedPrison: String? = null,
  val destinationType: DestinationType? = null,

  // Step 6: duplicate resolution
  val duplicateOf: UUID? = null,

  val hmtcsApiDataEnrichment: HmtcsApiDataEnrichment? = null,

  val warnings: List<String> = emptyList(),
)

enum class DestinationType {
  PRISON,
  PECS,
}
