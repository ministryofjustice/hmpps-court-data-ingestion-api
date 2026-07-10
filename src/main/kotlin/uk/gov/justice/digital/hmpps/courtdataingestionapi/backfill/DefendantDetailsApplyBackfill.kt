package uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.HmctsCourtDefendantApiClient
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtDocumentCaseRepository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.CourtCaseDefendantStore

@Component
class DefendantDetailsApplyBackfill(
  private val courtDocumentCaseRepository: CourtDocumentCaseRepository,
  private val hmctsCourtDefendantApiClient: HmctsCourtDefendantApiClient,
  private val courtCaseDefendantStore: CourtCaseDefendantStore,
) : Backfill<String> {

  override val id = "defendant-details-apply"
  override val concurrency = 4

  override fun selectBatch(cursor: String, batchSize: Int): BackfillBatch<String> {
    val items = courtDocumentCaseRepository.findDistinctCaseReferencesAfter(cursor, batchSize)
    val nextCursor = items.lastOrNull() ?: cursor
    return BackfillBatch(items, nextCursor)
  }

  override fun process(item: String) {
    val defendants = hmctsCourtDefendantApiClient.getDefendants(item)
    if (defendants.isEmpty()) {
      log.info("Case {} returned no defendants (unknown reference or no match); nothing to write", item)
      return
    }
    defendants.forEach { defendant ->
      courtCaseDefendantStore.upsert(
        defendant.defendantId,
        item,
        defendant.masterDefendantId,
        defendant.name,
        defendant.dateOfBirth,
      )
    }
    log.info("Case {}: upserted {} defendant row(s)", item, defendants.size)
  }

  companion object {
    private val log: Logger = LoggerFactory.getLogger(DefendantDetailsApplyBackfill::class.java)
  }
}
