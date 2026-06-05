package uk.gov.justice.digital.hmpps.courtdataingestionapi.controller

import org.springframework.boot.actuate.endpoint.annotation.Endpoint
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.extraction.ExtractionBackfillService

@Component
@Endpoint(id = "extractionbackfill")
class ExtractionBackfillEndpoint(
  private val backfillService: ExtractionBackfillService,
) {
  @WriteOperation
  fun trigger(): Map<String, String> {
    val started = backfillService.runBackfill()
    return mapOf("status" to if (started) "complete" else "already-running")
  }
}

@Component
@Endpoint(id = "hashbackfill")
class HashBackfillEndpoint(
  private val backfillService: ExtractionBackfillService,
) {
  @WriteOperation
  fun trigger(): Map<String, String> {
    val started = backfillService.runHashBackfill()
    return mapOf("status" to if (started) "complete" else "already-running")
  }
}

@Component
@Endpoint(id = "duplicatereconcile")
class DuplicateReconciliationEndpoint(
  private val reconciliationService: uk.gov.justice.digital.hmpps.courtdataingestionapi.service.ingestion.DuplicateReconciliationService,
) {
  @WriteOperation
  fun trigger(): Map<String, Any> {
    val summary = reconciliationService.reconcile()
    return mapOf("groups" to summary.groups, "linked" to summary.linked)
  }
}
