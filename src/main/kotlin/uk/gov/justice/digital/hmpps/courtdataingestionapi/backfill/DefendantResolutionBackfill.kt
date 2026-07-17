package uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtDocumentRepository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.DefendantMatchingService
import java.util.UUID

@Component
class DefendantResolutionBackfill(
  private val courtDocumentRepository: CourtDocumentRepository,
  private val defendantMatchingService: DefendantMatchingService,
) : Backfill<UUID> {

  override val id = "defendant-resolution-apply"
  override val concurrency = 4

  override fun selectBatch(cursor: String, batchSize: Int): BackfillBatch<UUID> {
    val afterId = parseCursorUUID(cursor)
    val items = courtDocumentRepository.findUnmatchedMasterDefendantIdsAfter(afterId, batchSize)
    val nextCursor = items.lastOrNull()?.toString() ?: cursor
    return BackfillBatch(items, nextCursor)
  }

  override fun process(item: UUID) {
    val masterDefendantId: UUID = item
    val matchedDocumentCount = defendantMatchingService.matchPrisonerForMasterDefendant(masterDefendantId)
    if (matchedDocumentCount > 0) {
      log.info(
        "Resolved defendant for masterDefendantId {}: {} document(s) now match a prisoner",
        item,
        matchedDocumentCount,
      )
    } else {
      log.debug("No documents matched for masterDefendantId {}", item)
    }
  }

  companion object {
    private val log: Logger = LoggerFactory.getLogger(DefendantResolutionBackfill::class.java)
  }
}
