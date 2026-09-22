package uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill

import org.springframework.data.domain.Limit
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtDocumentRepository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.CourtHearingService
import java.time.LocalDate
import java.util.UUID

@Component
class HearingDataBackfill(
  private val courtDocumentRepository: CourtDocumentRepository,
  private val courtHearingService: CourtHearingService,
) : Backfill<UUID> {

  override val id = "hearing-data-backfill"
  override val concurrency = 4

  override fun selectBatch(cursor: String, batchSize: Int): BackfillBatch<UUID> {
    val afterId = parseCursorUUID(cursor)
    val ids = courtDocumentRepository.findIdsWithUnpopulatedCourtHearingIngestedAfter(
      afterId,
      HEARING_DATA_AVAILABLE_FROM.atStartOfDay(),
      Limit.of(batchSize),
    )
    return BackfillBatch(ids, cursor)
  }

  @Transactional
  override fun process(item: UUID) {
    val document = courtDocumentRepository.findById(item).get()
    courtHearingService.fetchAndCreateHearingData(document)
  }

  companion object {
    val HEARING_DATA_AVAILABLE_FROM = LocalDate.of(2026, 9, 1)
  }
}
