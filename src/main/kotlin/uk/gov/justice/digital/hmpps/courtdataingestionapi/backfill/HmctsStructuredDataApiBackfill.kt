package uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.step.HmctsStructuredDataApiEnricher
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtDocumentRepository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.CourtHearingService

@Component
class HmctsStructuredDataApiBackfill(
  private val courtDocumentRepository: CourtDocumentRepository,
  private val enricher: HmctsStructuredDataApiEnricher,
  private val courtHearingService: CourtHearingService,
) : Backfill<CourtDocumentEntity> {

  override val id = "hmctsStructuredData"

  override fun selectBatch(
    cursor: String,
    batchSize: Int,
  ): BackfillBatch<CourtDocumentEntity> {
    val afterId = parseCursor(cursor)
    val items = courtDocumentRepository.findUnpopulatedCourtHearingData(afterId, batchSize)
    val nextCursor = items.lastOrNull()?.id?.toString() ?: cursor
    return BackfillBatch(items, nextCursor)
  }

  override fun process(item: CourtDocumentEntity) {
    val data =
      enricher.lookupStructureData(item.courtDocumentCases.map { it.caseReference }, item.hmctsCourtHearingId!!)
    courtHearingService.createOrUpdateCourtHearingData(item, data)
  }
}
