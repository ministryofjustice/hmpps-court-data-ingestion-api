package uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.MatchOutcome
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtDocumentRepository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.CourtDataIngestionService
import java.util.UUID

@Component
class ReanchorUnmatchedDryRunBackfill(
  private val courtDocumentRepository: CourtDocumentRepository,
  private val courtDataIngestionService: CourtDataIngestionService,
) : Backfill<UUID> {

  override val id = "reanchor-unmatched-dry-run"
  override val concurrency = 4

  override fun selectBatch(cursor: String, batchSize: Int): BackfillBatch<UUID> {
    val afterId = parseCursor(cursor)
    val items = courtDocumentRepository.findUnmatchedMasterDefendantIdsAfter(afterId, batchSize)
    val nextCursor = items.lastOrNull()?.toString() ?: cursor
    return BackfillBatch(items, nextCursor)
  }

  override fun process(item: UUID) {
    val preview = courtDataIngestionService.previewReattemptMatchForMaster(item) ?: return
    val wouldMatch = preview.outcome == MatchOutcome.MATCHED_ON_DEFENDANT_ID ||
      preview.outcome == MatchOutcome.MATCHED_ON_MASTER_DEFENDANT_ID
    log.info(
      "Preview only, nothing written: master {} would record {} across {} document(s) (would match: {})",
      item,
      preview.outcome,
      preview.documentCount,
      wouldMatch,
    )
  }

  companion object {
    private val log: Logger = LoggerFactory.getLogger(ReanchorUnmatchedDryRunBackfill::class.java)
  }
}
