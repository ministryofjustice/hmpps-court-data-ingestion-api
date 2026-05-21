package uk.gov.justice.digital.hmpps.courtdataingestionapi.controller

import io.swagger.v3.oas.annotations.Operation
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.task.TaskExecutor
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.extraction.ExtractionBackfillService

@RestController
@RequestMapping("/maintenance/extraction")
class ExtractionAdminController(
  private val backfillService: ExtractionBackfillService,
  @Qualifier("applicationTaskExecutor")
  private val taskExecutor: TaskExecutor,
) {
  @PostMapping("/backfill")
  @ResponseStatus(HttpStatus.ACCEPTED)
  @PreAuthorize("hasRole('ROLE_COURT_DATA_ADMIN')")
  @Operation(summary = "Extract or re-extract all stored documents missing a result at the active versions")
  fun startBackfill() {
    taskExecutor.execute { backfillService.runBackfill() }
  }
}
