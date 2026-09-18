package uk.gov.justice.digital.hmpps.courtdataingestionapi.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.PrisonCourtDocumentDay
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.PrisonCourtDocumentWeek
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.PrisonCourtDocumentService
import java.time.LocalDate

@RestController
@RequestMapping("/court-document/prison/{prisonCode}", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "PrisonCourtDocumentController", description = "Court document arrivals by prison")
@PreAuthorize("hasAnyRole('COURT_DATA_INGESTION__COURT_DATA_RO', 'COURTCASE_RELEASEDATE_SUPPORT')")
class PrisonCourtDocumentController(
  private val service: PrisonCourtDocumentService,
) {

  @GetMapping("/week")
  @Operation(
    summary = "A week of arrivals for the current population of a prison",
    description = "Any date in the week returns that whole week, Monday to Sunday.",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Successfully returns the week"),
      ApiResponse(responseCode = "401", description = "Unauthorised"),
      ApiResponse(responseCode = "403", description = "Forbidden"),
    ],
  )
  fun week(
    @Parameter(required = true, example = "LEI", description = "Prison code")
    @PathVariable prisonCode: String,
    @Parameter(required = true, description = "Any date in the week. Weeks run Monday to Sunday.")
    @RequestParam
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
    @Parameter(description = "Drop the cached roll and take a fresh one before querying")
    @RequestParam(defaultValue = "false") refreshRoll: Boolean,
  ): PrisonCourtDocumentWeek = service.weekFor(prisonCode, date, refreshRoll)

  @GetMapping("/day")
  @Operation(
    summary = "One day of arrivals for the current population of a prison",
    description = "Newest first",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Successfully returns the week"),
      ApiResponse(responseCode = "401", description = "Unauthorised"),
      ApiResponse(responseCode = "403", description = "Forbidden"),
    ],
  )
  fun day(
    @Parameter(required = true, example = "LEI", description = "Prison code")
    @PathVariable prisonCode: String,
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
    @Parameter(description = "Drop the cached roll and take a fresh one before querying")
    @RequestParam(defaultValue = "false") refreshRoll: Boolean,
  ): PrisonCourtDocumentDay = service.dayFor(prisonCode, date, refreshRoll)
}
