package uk.gov.justice.digital.hmpps.courtdataingestionapi.controller

import org.springframework.boot.actuate.endpoint.annotation.Endpoint
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.extraction.ExtractionBackfillService

@Component
@Endpoint(id = "extraction-backfill")
class ExtractionBackfillEndpoint(
  private val backfillService: ExtractionBackfillService,
) {
  @WriteOperation
  fun trigger(): Map<String, String> {
    val started = backfillService.runBackfill()
    return mapOf("status" to if (started) "complete" else "already-running")
  }
}
