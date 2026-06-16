package uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.step.HmctsStructuredDataApiEnricher
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtDocumentRepository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.CourtHearingService
import java.util.UUID

@Component
class HmctsStructuredDataApiBackfill(
  private val courtDocumentRepository: CourtDocumentRepository,
  private val enricher: HmctsStructuredDataApiEnricher,
  private val courtHearingService: CourtHearingService,
) : Backfill<UUID> {

  override val id = "hmctsStructuredData"

  override fun selectBatch(
    cursor: String,
    batchSize: Int,
  ): BackfillBatch<UUID> {
    val afterId = parseCursor(cursor)
    val items = courtDocumentRepository.findUnpopulatedCourtHearingData(afterId, batchSize).map { it.id }
    val nextCursor = items.lastOrNull()?.toString() ?: cursor
    return BackfillBatch(items, nextCursor)
  }

  override fun process(item: UUID) {
    val courtDocument = courtDocumentRepository.findById(item).get()
    val data =
      enricher.lookupStructureData(courtDocument.courtDocumentCases.map { it.caseReference }, courtDocument.hmctsCourtHearingId!!)
    courtHearingService.createOrUpdateCourtHearingData(courtDocument, data)
  }
}
