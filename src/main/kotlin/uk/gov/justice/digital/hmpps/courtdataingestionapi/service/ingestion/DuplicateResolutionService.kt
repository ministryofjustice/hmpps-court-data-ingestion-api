package uk.gov.justice.digital.hmpps.courtdataingestionapi.service.ingestion

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.ingestion.DuplicateResolutionOutcome
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtDocumentRepository
import java.util.UUID

@Service
class DuplicateResolutionService(
  private val courtDocumentRepository: CourtDocumentRepository,
) {

  private val log = LoggerFactory.getLogger(DuplicateResolutionService::class.java)

  fun resolve(
    currentDocumentId: UUID,
    downloadedFileSha256: String?,
    extractedTextSha256: String?,
  ): DuplicateResolutionOutcome? {
    if (downloadedFileSha256 == null && extractedTextSha256 == null) {
      return null
    }

    extractedTextSha256?.let { hash ->
      firstOtherMatch(courtDocumentRepository.findByExtractedTextSha256(hash), currentDocumentId)?.let { duplicateId ->
        log.debug("Duplicate of {} found via extracted text hash", duplicateId)
        return DuplicateResolutionOutcome(
          duplicateOf = duplicateId,
          reason = "matched_on_extracted_text_sha256",
        )
      }
    }

    downloadedFileSha256?.let { hash ->
      firstOtherMatch(courtDocumentRepository.findByDownloadedFileSha256(hash), currentDocumentId)?.let { duplicateId ->
        log.debug("Duplicate of {} found via downloaded file hash", duplicateId)
        return DuplicateResolutionOutcome(
          duplicateOf = duplicateId,
          reason = "matched_on_downloaded_file_sha256",
        )
      }
    }

    return null
  }

  // Returns the prisonDocumentId of the first candidate that is a different document.
  // prisonDocumentId is the cross-system identifier the document store understands,
  // so that is what gets persisted as duplicate_of. mapNotNull guards against a
  // candidate row with a null prisonDocumentId yielding a duplicateOf of null.
  private fun firstOtherMatch(
    candidates: List<CourtDocumentEntity>,
    currentDocumentId: UUID,
  ): UUID? = candidates
    .mapNotNull { it.prisonDocumentId }
    .firstOrNull { it != currentDocumentId }
}
