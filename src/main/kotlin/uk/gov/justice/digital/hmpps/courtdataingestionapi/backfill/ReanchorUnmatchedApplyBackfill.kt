package uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtDocumentRepository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.CourtDataIngestionService
import java.util.UUID

@Component
class ReanchorUnmatchedApplyBackfill(
  private val courtDocumentRepository: CourtDocumentRepository,
  private val courtDataIngestionService: CourtDataIngestionService,
) : Backfill<UUID> {

  override val id = "reanchor-unmatched-apply"
  override val concurrency = 4

  override fun selectBatch(cursor: String, batchSize: Int): BackfillBatch<UUID> {
    val afterId = parseCursor(cursor)
    val items = courtDocumentRepository.findUnmatchedMasterDefendantIdsAfter(afterId, batchSize)
    val nextCursor = items.lastOrNull()?.toString() ?: cursor
    return BackfillBatch(items, nextCursor)
  }

  override fun process(item: UUID) {
    val matched = courtDataIngestionService.reattemptMatchForMaster(item)
    if (matched > 0) {
      log.info("Reanchored document {}: {} document(s) now match a prisoner", item, matched)
    }
  }

  companion object {
    private val log: Logger = LoggerFactory.getLogger(ReanchorUnmatchedApplyBackfill::class.java)
  }
}
