package uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.step.HmctsStructuredDataApiEnricher
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtDocumentRepository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.CourtHearingService

@Component
class CourtRegisterApiBackfill(
  private val courtDocumentRepository: CourtDocumentRepository,
  private val enricher: HmctsStructuredDataApiEnricher,
  private val courtHearingService: CourtHearingService,
  private val metadataBackfill: MirrorBackfill,
) : Backfill<CourtDocumentEntity> {

  override val id = "court-register-api"

  override fun selectBatch(
    cursor: String,
    batchSize: Int,
  ): BackfillBatch<CourtDocumentEntity> {
    val afterId = parseCursorUUID(cursor)
    val items = courtDocumentRepository.findUnpopulatedCourtRegisterData(afterId, batchSize)
    val nextCursor = items.lastOrNull()?.id?.toString() ?: cursor
    return BackfillBatch(items, nextCursor)
  }

  @Transactional
  override fun process(item: CourtDocumentEntity) {
    val hearing = item.courtHearing ?: return
    val data = enricher.lookupCourtRegisterData(hearing)
    courtHearingService.createOrUpdateCourtHearingData(item, data)
    // Also mirror metadata with updated values
    metadataBackfill.process(item)
  }
}
