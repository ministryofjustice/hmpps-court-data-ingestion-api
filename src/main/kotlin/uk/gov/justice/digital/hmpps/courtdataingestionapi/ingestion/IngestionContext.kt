package uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion

import java.util.UUID

data class IngestionContext(
  val prisonEmailAddress: String?,
  val prisonDocumentId: UUID?,

  // Step 1–2: raw file + hash
  val downloadedFileBytes: ByteArray? = null,
  val downloadedFileSha256: String? = null,

  // Step 3–4: extracted text + hash
  val extractedText: String? = null,
  val extractedTextSha256: String? = null,

  // Step 5: destination classification
  val addressedPrison: String? = null,
  val destinationType: DestinationType? = null,


  val warnings: List<String> = emptyList(),
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as IngestionContext

    if (prisonEmailAddress != other.prisonEmailAddress) return false
    if (prisonDocumentId != other.prisonDocumentId) return false
    if (!downloadedFileBytes.contentEquals(other.downloadedFileBytes)) return false
    if (downloadedFileSha256 != other.downloadedFileSha256) return false
    if (extractedText != other.extractedText) return false
    if (extractedTextSha256 != other.extractedTextSha256) return false
    if (addressedPrison != other.addressedPrison) return false
    if (destinationType != other.destinationType) return false
    if (warnings != other.warnings) return false

    return true
  }

  override fun hashCode(): Int {
    var result = prisonEmailAddress?.hashCode() ?: 0
    result = 31 * result + (prisonDocumentId?.hashCode() ?: 0)
    result = 31 * result + (downloadedFileBytes?.contentHashCode() ?: 0)
    result = 31 * result + (downloadedFileSha256?.hashCode() ?: 0)
    result = 31 * result + (extractedText?.hashCode() ?: 0)
    result = 31 * result + (extractedTextSha256?.hashCode() ?: 0)
    result = 31 * result + (addressedPrison?.hashCode() ?: 0)
    result = 31 * result + (destinationType?.hashCode() ?: 0)
    result = 31 * result + warnings.hashCode()
    return result
  }
}

enum class DestinationType {
  PRISON,
  PECS,
}
