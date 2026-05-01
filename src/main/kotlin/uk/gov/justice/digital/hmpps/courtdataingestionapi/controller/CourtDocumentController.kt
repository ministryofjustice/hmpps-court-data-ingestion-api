package uk.gov.justice.digital.hmpps.courtdataingestionapi.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtDocumentView
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.CourtDocumentService
import java.util.UUID

@RestController
@RequestMapping("/court-document", produces = [MediaType.APPLICATION_JSON_VALUE])
class CourtDocumentController(
  private val courtDocumentService: CourtDocumentService,
) {

  @PostMapping("/{courtDocumentId}/view")
  @PreAuthorize("hasRole('COURT_DATA_INGESTION__COURT_DATA_RW')")
  @Operation(
    summary = "Record a viewing of a court document",
    description = "Records that a given user has viewed a court document.",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Successfully recorded a viewing of court document."),
      ApiResponse(responseCode = "401", description = "Unauthorized - valid Oauth2 token required"),
      ApiResponse(responseCode = "403", description = "Forbidden - requires appropriate role"),
    ],
  )
  fun view(@PathVariable courtDocumentId: UUID, @RequestBody courtDocumentView: CourtDocumentView): ResponseEntity<CourtDocumentView> {
    courtDocumentService.recordDocumentView(courtDocumentId, courtDocumentView)
    return ResponseEntity.ok(courtDocumentView)
  }
}
