package uk.gov.justice.digital.hmpps.courtdataingestionapi.service.ingestion

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtDocumentRepository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.FileService

data class ReconciliationSummary(
  val groups: Int,
  val linked: Int,
)

@Service
class DuplicateReconciliationService(
  private val courtDocumentRepository: CourtDocumentRepository,
  private val fileService: FileService,
) {

  private val log = LoggerFactory.getLogger(DuplicateReconciliationService::class.java)

  @Transactional
  fun reconcile(): ReconciliationSummary {
    var groups = 0
    var linked = 0

    courtDocumentRepository.findDuplicatedExtractedTextHashes().forEach { hash ->
      val rows = courtDocumentRepository.findByExtractedTextSha256(hash)
        .sortedWith(compareBy(nullsLast(naturalOrder())) { it.ingestionAt })
      if (rows.size < 2) return@forEach

      groups++
      val canonical = rows.firstOrNull { it.duplicateOf == null } ?: rows.first()

      rows
        .filter { it.id != canonical.id && it.duplicateOf != canonical.prisonDocumentId }
        .forEach { duplicate ->
          duplicate.duplicateOf = canonical.prisonDocumentId
          courtDocumentRepository.save(duplicate)
          runCatching {
            fileService.stampIngestionMetadata(
              prisonDocumentId = duplicate.prisonDocumentId,
              deliverySource = duplicate.deliverySource,
              downloadedFileSha256 = duplicate.downloadedFileSha256,
              extractedTextSha256 = duplicate.extractedTextSha256,
              duplicateOf = canonical.prisonDocumentId,
            )
          }.onFailure { log.warn("Reconcile metadata stamp failed for {}", duplicate.id, it) }
          linked++
        }
    }

    log.info("Reconciliation complete: {} groups, {} documents linked", groups, linked)
    return ReconciliationSummary(groups, linked)
  }
}
