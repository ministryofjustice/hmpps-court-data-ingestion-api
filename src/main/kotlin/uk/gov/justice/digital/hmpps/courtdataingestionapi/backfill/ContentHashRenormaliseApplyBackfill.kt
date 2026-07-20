package uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.HmppsDocumentManagementApi
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtDocumentRepository

/**
 * Applies the content-hash renormalisation. Same recomputation as
 * [ContentHashRenormaliseDryRunBackfill] via the shared [ContentHashRecomputer], but where the
 * hash has changed this writes it to the row and pushes it to the document store. DMS already
 * calls redetermineCanonicalFor unconditionally inside setFileContentHash, so no separate trigger
 * is needed here. Run only after reviewing the dry-run output.
 *
 * Trigger via POST /actuator/backfill {"id": "content-hash-renormalise-apply"}.
 */
@Component
class ContentHashRenormaliseApplyBackfill(
  private val courtDocumentRepository: CourtDocumentRepository,
  private val documentManagementApi: HmppsDocumentManagementApi,
  private val recomputer: ContentHashRecomputer,
) : Backfill<CourtDocumentEntity> {

  override val id = "content-hash-renormalise-apply"
  override val concurrency = 4

  override fun selectBatch(cursor: String, batchSize: Int): BackfillBatch<CourtDocumentEntity> {
    val afterId = parseCursorUUID(cursor)
    val items = courtDocumentRepository.findHashedAfter(afterId, batchSize)
    val nextCursor = items.lastOrNull()?.id?.toString() ?: cursor
    return BackfillBatch(items, nextCursor)
  }

  override fun process(item: CourtDocumentEntity) {
    val recomputation = recomputer.recompute(item) ?: return
    if (recomputation.changed) {
      item.extractedTextSha256 = recomputation.newHash
      courtDocumentRepository.save(item)
      documentManagementApi.setFileContentHash(item.prisonDocumentId, recomputation.newHash)
      log.info(
        "Applied: document {} (court_document {}) content hash {} -> {}",
        item.prisonDocumentId,
        item.id,
        recomputation.currentHash,
        recomputation.newHash,
      )
    }
  }

  companion object {
    private val log: Logger = LoggerFactory.getLogger(ContentHashRenormaliseApplyBackfill::class.java)
  }
}
