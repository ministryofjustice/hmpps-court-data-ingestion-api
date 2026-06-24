package uk.gov.justice.digital.hmpps.courtdataingestionapi.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtHearing
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.CourtHearingService
import java.util.UUID

@RestController
@RequestMapping("/court-hearing", produces = [MediaType.APPLICATION_JSON_VALUE])
class CourtHearingController(
  private val courtHearingService: CourtHearingService,
) {

  @GetMapping("/{courtHearingId}")
  @PreAuthorize("hasAnyRole('COURT_DATA_INGESTION__COURT_DATA_RO', 'COURT_DATA_INGESTION__COURT_DATA_RW')")
  @Operation(
    summary = "Get court hearing info",
    description = "Gets court hearing data ingested from CP.",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Successfully gets court hearing."),
      ApiResponse(responseCode = "401", description = "Unauthorized - valid Oauth2 token required"),
      ApiResponse(responseCode = "403", description = "Forbidden - requires appropriate role"),
    ],
  )
  fun getCourtHearings(
    @PathVariable("courtHearingId") courtHearingId: UUID,
  ): CourtHearing = courtHearingService.getCourtHearing(courtHearingId)
}
