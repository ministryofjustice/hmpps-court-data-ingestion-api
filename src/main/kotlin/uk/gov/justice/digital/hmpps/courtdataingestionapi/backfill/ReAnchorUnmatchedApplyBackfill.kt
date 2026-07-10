package uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtDocumentRepository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.CourtDataIngestionService

@Component
class ReAnchorUnmatchedApplyBackfill(
  private val courtDocumentRepository: CourtDocumentRepository,
  private val courtDataIngestionService: CourtDataIngestionService,
) : Backfill<CourtDocumentEntity> {

  override val id = "re-anchor-unmatched-apply"
  override val concurrency = 4

  override fun selectBatch(cursor: String, batchSize: Int): BackfillBatch<CourtDocumentEntity> {
    val afterId = parseCursor(cursor)
    val items = courtDocumentRepository.findUnmatchedAfter(afterId, batchSize)
    val nextCursor = items.lastOrNull()?.id?.toString() ?: cursor
    return BackfillBatch(items, nextCursor)
  }

  override fun process(item: CourtDocumentEntity) {
    val matched = courtDataIngestionService.reAttemptMatch(item.id)
    if (matched) {
      log.info("Re-anchored: court_document {} now matches a prisoner", item.id)
    }
  }

  companion object {
    private val log: Logger = LoggerFactory.getLogger(ReAnchorUnmatchedApplyBackfill::class.java)
  }
}
