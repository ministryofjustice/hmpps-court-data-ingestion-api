//package uk.gov.justice.digital.hmpps.courtdataingestionapi.controller
//
//import io.swagger.v3.oas.annotations.Operation
//import io.swagger.v3.oas.annotations.Parameter
//import io.swagger.v3.oas.annotations.responses.ApiResponse
//import io.swagger.v3.oas.annotations.responses.ApiResponses
//import jakarta.validation.Valid
//import org.slf4j.LoggerFactory
//import org.springframework.http.MediaType
//import org.springframework.http.ResponseEntity
//import org.springframework.security.access.prepost.PreAuthorize
//import org.springframework.web.bind.annotation.GetMapping
//import org.springframework.web.bind.annotation.PathVariable
//import org.springframework.web.bind.annotation.PostMapping
//import org.springframework.web.bind.annotation.RequestBody
//import org.springframework.web.bind.annotation.RequestMapping
//import org.springframework.web.bind.annotation.RequestParam
//import org.springframework.web.bind.annotation.RestController
//import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentEntity
//import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtDocument
//import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtDocumentView
//import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.DocumentSearchRequest
//import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.CourtDocumentService
//import java.util.UUID
//
//@RestController
//@RequestMapping("/unmatched-documents", produces = [MediaType.APPLICATION_JSON_VALUE])
//class UnmatchedDocumentController(
//  private val courtDocumentService: CourtDocumentService,
//) {
//
////  @GetMapping("/{prisonDocumentId}")
////  @PreAuthorize("hasRole('COURT_DATA_INGESTION__COURT_DATA_RW')")
////  @Operation(
////    summary = "Record a viewing of an unmatched court document",
////    description = "Records that a given user has viewed an unmatched court document.",
////  )
////  @ApiResponses(
////    value = [
////      ApiResponse(responseCode = "200", description = "Successfully recorded a viewing of court document."),
////      ApiResponse(responseCode = "401", description = "Unauthorized - valid Oauth2 token required"),
////      ApiResponse(responseCode = "403", description = "Forbidden - requires appropriate role"),
////    ],
////  )
////  fun view(@PathVariable prisonDocumentId: UUID, @RequestBody courtDocumentView: CourtDocumentView): ResponseEntity<CourtDocumentView> {
////    courtDocumentService.recordDocumentView(prisonDocumentId, courtDocumentView)
////    return ResponseEntity.ok(courtDocumentView)
////  }
//
//  @PostMapping("/list")
//  @PreAuthorize("hasRole('COURT_DATA_INGESTION__COURT_DATA_RO')")
//  @Operation(
//    summary = "Record a viewing of an unmatched court document",
//    description = "Records that a given user has viewed an unmatched court document.",
//  )
//  @ApiResponses(
//    value = [
//      ApiResponse(responseCode = "200", description = "Successfully recorded a viewing of court document."),
//      ApiResponse(responseCode = "401", description = "Unauthorized - valid Oauth2 token required"),
//      ApiResponse(responseCode = "403", description = "Forbidden - requires appropriate role"),
//    ],
//  )
//  fun list(@Valid @RequestBody @Parameter(
//      description = "The search parameters to use to filter documents", required = true,
//    ) searchRequest: DocumentSearchRequest): List<CourtDocument> {
//    val docCount = courtDocumentService.getUnmatchedDocumentsCount(searchRequest)
//    log.debug("Found {} documents unmatched", docCount)
//    val docs = courtDocumentService.getUnmatchedDocuments(searchRequest)
//    return docs
//  }
//
//  companion object {
//    private val log = LoggerFactory.getLogger(this::class.java)
//  }
//}
