package uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.HmppsDocumentManagementApi
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction.ExtractedTextNormaliser
import uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction.PdfTextExtractor

data class ContentHashRecomputation(
  val currentHash: String,
  val newHash: String,
) {
  val changed: Boolean get() = newHash != currentHash
}

@Component
class ContentHashRecomputer(
  private val documentManagementApi: HmppsDocumentManagementApi,
  private val pdfTextExtractor: PdfTextExtractor,
  private val normaliser: ExtractedTextNormaliser,
) {
  fun recompute(item: CourtDocumentEntity): ContentHashRecomputation? {
    val currentHash = item.extractedTextSha256?.takeIf { it.isNotBlank() } ?: return null
    val bytes = documentManagementApi.downloadFile(item.prisonDocumentId)
    val text = pdfTextExtractor.extractText(bytes) ?: return null
    return ContentHashRecomputation(currentHash, normaliser.getNormalisedHash(text))
  }
}
