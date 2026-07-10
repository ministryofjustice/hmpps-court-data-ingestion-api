package uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.HmctsCourtDefendantApiClient
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtDocumentCaseRepository

@Component
class DefendantDetailsDryRunBackfill(
  private val courtDocumentCaseRepository: CourtDocumentCaseRepository,
  private val hmctsCourtDefendantApiClient: HmctsCourtDefendantApiClient,
) : Backfill<String> {

  override val id = "defendant-details-dry-run"
  override val concurrency = 4

  override fun selectBatch(cursor: String, batchSize: Int): BackfillBatch<String> {
    val items = courtDocumentCaseRepository.findDistinctCaseReferencesAfter(cursor, batchSize)
    val nextCursor = items.lastOrNull() ?: cursor
    return BackfillBatch(items, nextCursor)
  }

  override fun process(item: String) {
    val defendants = hmctsCourtDefendantApiClient.getDefendants(item)
    if (defendants.isEmpty()) {
      log.info("Preview only: case {} returned no defendants (unknown reference or no match)", item)
      return
    }
    val masters = defendants.map { it.masterDefendantId }.distinct()
    log.info(
      "Preview only, nothing written: case {} -> {} defendant(s) across {} master id(s)",
      item,
      defendants.size,
      masters.size,
    )
  }

  companion object {
    private val log: Logger = LoggerFactory.getLogger(DefendantDetailsDryRunBackfill::class.java)
  }
}
