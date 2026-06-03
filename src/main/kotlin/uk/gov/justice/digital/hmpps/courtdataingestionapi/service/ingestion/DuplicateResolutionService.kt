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
      matchAgainst(
        duplicateCandidates = courtDocumentRepository.findByExtractedTextSha256(hash),
        documentId = currentDocumentId,
        reason = "matched_on_extracted_text_sha256",
      )?.let { return it }
    }

    downloadedFileSha256?.let { hash ->
      matchAgainst(
        duplicateCandidates = courtDocumentRepository.findByDownloadedFileSha256(hash),
        documentId = currentDocumentId,
        reason = "matched_on_downloaded_file_sha256",
      )?.let { return it }
    }

    return null
  }

  private fun matchAgainst(
    duplicateCandidates: List<CourtDocumentEntity>,
    documentId: UUID,
    reason: String,
  ): DuplicateResolutionOutcome? = findFirstDuplicateDocumentId(duplicateCandidates, documentId)?.let { duplicateId ->
    log.debug("Duplicate of {} found via {}", duplicateId, reason)
    DuplicateResolutionOutcome(duplicateOf = duplicateId, reason = reason)
  }

  private fun findFirstDuplicateDocumentId(
    duplicateCandidates: List<CourtDocumentEntity>,
    documentId: UUID,
  ): UUID? = duplicateCandidates
    .mapNotNull { it.prisonDocumentId }
    .firstOrNull { it != documentId }
}
