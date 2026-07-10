package uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.MatchOutcome
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtDocumentRepository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.CourtDataIngestionService

@Component
class ReAnchorUnmatchedDryRunBackfill(
  private val courtDocumentRepository: CourtDocumentRepository,
  private val courtDataIngestionService: CourtDataIngestionService,
) : Backfill<CourtDocumentEntity> {

  override val id = "re-anchor-unmatched-dry-run"
  override val concurrency = 4

  override fun selectBatch(cursor: String, batchSize: Int): BackfillBatch<CourtDocumentEntity> {
    val afterId = parseCursor(cursor)
    val items = courtDocumentRepository.findUnmatchedAfter(afterId, batchSize)
    val nextCursor = items.lastOrNull()?.id?.toString() ?: cursor
    return BackfillBatch(items, nextCursor)
  }

  override fun process(item: CourtDocumentEntity) {
    val outcome = courtDataIngestionService.previewReAttemptMatch(item.id) ?: return
    val wouldMatch = outcome == MatchOutcome.MATCHED_ON_DEFENDANT_ID || outcome == MatchOutcome.MATCHED_ON_MASTER_DEFENDANT_ID
    log.info(
      "Preview only, nothing written: court_document {} would record {} (would match: {})",
      item.id,
      outcome,
      wouldMatch,
    )
  }

  companion object {
    private val log: Logger = LoggerFactory.getLogger(ReAnchorUnmatchedDryRunBackfill::class.java)
  }
}
