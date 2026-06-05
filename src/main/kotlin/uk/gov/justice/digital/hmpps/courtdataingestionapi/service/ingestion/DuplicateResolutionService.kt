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

    (extractedTextSha256 ?: downloadedFileSha256)?.let { lockHash ->
      courtDocumentRepository.lockOnContentHash(lockHash)
    }

    extractedTextSha256?.let { hash ->
      resolveAgainst(
        candidates = courtDocumentRepository.findByExtractedTextSha256(hash),
        currentDocumentId = currentDocumentId,
        reason = "matched_on_extracted_text_sha256",
      )?.let { return it }
    }

    downloadedFileSha256?.let { hash ->
      resolveAgainst(
        candidates = courtDocumentRepository.findByDownloadedFileSha256(hash),
        currentDocumentId = currentDocumentId,
        reason = "matched_on_downloaded_file_sha256",
      )?.let { return it }
    }

    return null
  }

  private fun resolveAgainst(
    candidates: List<CourtDocumentEntity>,
    currentDocumentId: UUID,
    reason: String,
  ): DuplicateResolutionOutcome? = chooseCanonical(candidates, currentDocumentId)?.let { canonical ->
    log.debug("Duplicate of {} found via {}", canonical, reason)
    DuplicateResolutionOutcome(duplicateOf = canonical, reason = reason)
  }

  private fun chooseCanonical(
    candidates: List<CourtDocumentEntity>,
    currentDocumentId: UUID,
  ): UUID? {
    val others = candidates
      .filter { it.prisonDocumentId != currentDocumentId }
      .sortedWith(compareBy(nullsLast(naturalOrder())) { it.ingestionAt })

    if (others.isEmpty()) return null

    val head = others.firstOrNull { it.duplicateOf == null }
    return head?.prisonDocumentId
      ?: others.first().duplicateOf
      ?: others.first().prisonDocumentId
  }
}
