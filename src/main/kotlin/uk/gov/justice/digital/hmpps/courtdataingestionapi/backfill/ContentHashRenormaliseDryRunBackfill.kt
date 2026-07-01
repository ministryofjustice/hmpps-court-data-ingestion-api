package uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtDocumentRepository

@Component
class ContentHashRenormaliseDryRunBackfill(
  private val courtDocumentRepository: CourtDocumentRepository,
  private val recomputer: ContentHashRecomputer,
) : Backfill<CourtDocumentEntity> {

  override val id = "content-hash-renormalise-dry-run"
  override val concurrency = 4

  override fun selectBatch(cursor: String, batchSize: Int): BackfillBatch<CourtDocumentEntity> {
    val afterId = parseCursor(cursor)
    val items = courtDocumentRepository.findHashedAfter(afterId, batchSize)
    val nextCursor = items.lastOrNull()?.id?.toString() ?: cursor
    return BackfillBatch(items, nextCursor)
  }

  override fun process(item: CourtDocumentEntity) {
    val recomputation = recomputer.recompute(item) ?: return
    if (recomputation.changed) {
      log.info(
        "Preview only, nothing written: document {} (court_document {}) content hash {} -> {}",
        item.prisonDocumentId,
        item.id,
        recomputation.currentHash,
        recomputation.newHash,
      )
    }
  }

  companion object {
    private val log: Logger = LoggerFactory.getLogger(ContentHashRenormaliseDryRunBackfill::class.java)
  }
}
