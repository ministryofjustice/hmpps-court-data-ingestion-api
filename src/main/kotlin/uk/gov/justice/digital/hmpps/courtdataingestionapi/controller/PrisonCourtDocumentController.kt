package uk.gov.justice.digital.hmpps.courtdataingestionapi.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.format.annotation.DateTimeFormat
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
@RequestMapping("/court-document/prison/{prisonCode}")
@Tag(name = "PrisonCourtDocumentController", description = "Court documents received for a prison's current population")
@PreAuthorize("hasRole('COURT_DATA_INGESTION__COURT_DATA_RO')")
class PrisonCourtDocumentController(
  private val service: PrisonCourtDocumentService,
) {

  @GetMapping("/week")
  @Operation(summary = "Documents received in the week containing the date, Monday to Sunday")
  fun week(
    @PathVariable prisonCode: String,
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
  ): PrisonCourtDocumentWeek = service.week(prisonCode.uppercase(), date)

  @GetMapping("/day")
  @Operation(summary = "Documents received on a day, grouped by hearing")
  fun day(
    @PathVariable prisonCode: String,
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
  ): PrisonCourtDocumentDay = service.day(prisonCode.uppercase(), date)
}
