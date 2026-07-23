package uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.step.HmctsStructuredDataApiEnricher
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtDocumentRepository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.CourtHearingService
import java.util.UUID

@Component
class CourtRegisterApiBackfill(
  private val courtDocumentRepository: CourtDocumentRepository,
  private val enricher: HmctsStructuredDataApiEnricher,
  private val courtHearingService: CourtHearingService,
  private val metadataBackfill: MirrorBackfill,
) : Backfill<UUID> {

  override val id = "court-register-api"

  override fun selectBatch(
    cursor: String,
    batchSize: Int,
  ): BackfillBatch<UUID> {
    val afterId = parseCursorUUID(cursor)
    val items = courtDocumentRepository.findUnpopulatedCourtRegisterData(afterId, batchSize).map { it.id }
    val nextCursor = items.lastOrNull()?.toString() ?: cursor
    return BackfillBatch(items, nextCursor)
  }

  @Transactional
  override fun process(item: UUID) {
    val document = courtDocumentRepository.findById(item).get()
    val hearing = document.courtHearing ?: return
    val data = enricher.lookupCourtRegisterData(hearing)
    courtHearingService.createOrUpdateCourtHearingData(document, data)
    // Also mirror metadata with updated values
    metadataBackfill.process(item)
  }
}
