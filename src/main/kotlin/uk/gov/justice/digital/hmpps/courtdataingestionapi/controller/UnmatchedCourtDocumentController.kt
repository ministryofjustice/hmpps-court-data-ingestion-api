package uk.gov.justice.digital.hmpps.courtdataingestionapi.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtDocument
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.UnmatchedCourtDocumentService
import java.util.UUID

@RestController
@RequestMapping("/unmatched-document", produces = [MediaType.APPLICATION_JSON_VALUE])
class UnmatchedCourtDocumentController(
  private val unmatchedDocumentService: UnmatchedCourtDocumentService,
) {

  @GetMapping
  @PreAuthorize("hasRole('COURTCASE_RELEASEDATE_SUPPORT')")
  @Operation(
    summary = "Collect data of unmatched court documents",
    description = "Retrieve information of the given unmatched court documents.",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Successfully collected the requested unmatched court documents."),
      ApiResponse(responseCode = "401", description = "Unauthorized - valid Oauth2 token required"),
      ApiResponse(responseCode = "403", description = "Forbidden - requires ROLE_COURTCASE_RELEASEDATE_SUPPORT"),
    ],
  )
  fun getDocuments(@RequestParam(name = "prisonDocumentIds", required = true) prisonDocumentIds: List<UUID>): List<CourtDocument> = unmatchedDocumentService.getDocuments(prisonDocumentIds)
}
