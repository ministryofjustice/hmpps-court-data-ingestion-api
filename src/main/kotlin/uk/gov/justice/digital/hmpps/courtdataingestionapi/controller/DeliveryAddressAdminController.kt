package uk.gov.justice.digital.hmpps.courtdataingestionapi.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.DeliveryCategory
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.ClassifyAddressPreview
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.ClassifyAddressResult
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.DeliveryAddressAdminService
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.UnknownCategoryException
import java.net.URI
import java.security.Principal

data class DeliveryAddressResponse(
  val emailAddress: String,
  val documentCount: Int,
  val matchedToPersonCount: Int,
  val firstSeen: String,
  val lastSeen: String,
  val recentDocumentTypes: List<String>,
)

data class CreateCategoryRequest(
  @field:Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{1,31}$", message = "Code must be 2 to 32 characters, letters, digits and underscores, starting with a letter")
  val code: String,
  @field:Pattern(regexp = "^[\\p{L}\\p{N} '()&/-]{1,128}$", message = "Name must be 1 to 128 characters of letters, digits, spaces and simple punctuation")
  val name: String,
  val requiresPrisonCode: Boolean = false,
  val unmatchedNeedsReview: Boolean = true,
)

data class ClassifyAddressRequest(
  @field:Email @field:Size(max = 254) val emailAddress: String,
  @field:Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{1,31}$") val categoryCode: String,
  @field:Pattern(regexp = "^[A-Z]{2,6}$", message = "Prison code must be 2 to 6 upper case letters")
  val prisonCode: String? = null,
)

@RestController
@RequestMapping("/admin", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "DeliveryAddressAdminController", description = "Where court documents are addressed, and how that traffic is classified")
@PreAuthorize("hasRole('COURTCASE_RELEASEDATE_SUPPORT')")
class DeliveryAddressAdminController(
  private val service: DeliveryAddressAdminService,
) {

  @GetMapping("/delivery-addresses")
  @Operation(
    summary = "Addresses court documents have been delivered to",
    description = "Grouped by address rather than by document. classified=false returns the " +
      "addresses with no category, which is the support worklist. PECS traffic is excluded: it " +
      "is correctly delivered without a prison and is not a gap.",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Successfully returns the delivery address list"),
      ApiResponse(responseCode = "401", description = "Unauthorized - valid Oauth2 token required"),
      ApiResponse(responseCode = "403", description = "Forbidden - requires ROLE_COURTCASE_RELEASEDATE_SUPPORT"),
    ],
  )
  fun deliveryAddresses(
    @Parameter(description = "Only addresses with no category. The only supported value today is false.")
    @RequestParam(defaultValue = "false") classified: Boolean,
  ): List<DeliveryAddressResponse> = service.unclassifiedAddresses().map {
    DeliveryAddressResponse(
      emailAddress = it.emailAddress,
      documentCount = it.documentCount,
      matchedToPersonCount = it.matchedToPersonCount,
      firstSeen = it.firstSeen.toLocalDate().toString(),
      lastSeen = it.lastSeen.toLocalDate().toString(),
      recentDocumentTypes = it.recentDocumentTypes,
    )
  }

  @GetMapping("/delivery-categories")
  @Operation(summary = "Delivery categories")
  fun categories(): List<DeliveryCategory> = service.categories()

  @PostMapping("/delivery-categories")
  @Operation(
    summary = "Create a delivery category",
    description = "Categories are data, so new ones need no code change. A category created here " +
      "is inert until its flags are widened deliberately.",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "201", description = "Category created"),
      ApiResponse(responseCode = "409", description = "A category with that code already exists"),
    ],
  )
  fun createCategory(
    @Valid @RequestBody request: CreateCategoryRequest,
    principal: Principal?,
  ): ResponseEntity<Void> {
    val code = request.code.uppercase()

    service.createCategory(
      DeliveryCategory(
        code = code,
        name = request.name,
        requiresPrisonCode = request.requiresPrisonCode,
        unmatchedNeedsReview = request.unmatchedNeedsReview,
      ),
      principal?.name,
    ) ?: return ResponseEntity.status(HttpStatus.CONFLICT).build()

    return ResponseEntity.created(URI.create("/admin/delivery-categories/$code")).build()
  }

  @PostMapping("/delivery-addresses/preview")
  @Operation(
    summary = "Dry run a classification",
    description = "Same resolution path as the apply, so the counts are real.",
  )
  fun preview(@Valid @RequestBody request: ClassifyAddressRequest): ResponseEntity<ClassifyAddressPreview> = try {
    ResponseEntity.ok(service.preview(request.emailAddress, request.categoryCode, request.prisonCode))
  } catch (_: UnknownCategoryException) {
    ResponseEntity.notFound().build()
  }

  @PostMapping("/delivery-addresses")
  @Operation(
    summary = "Classify a delivery address and re-resolve its documents",
    description = "Creates or replaces the mapping, then triggers the reresolve backfill.",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Mapping created and re-resolution requested"),
      ApiResponse(responseCode = "404", description = "No category with that code"),
      ApiResponse(responseCode = "422", description = "The category requires a prison code and none was given"),
    ],
  )
  fun classify(
    @Valid @RequestBody request: ClassifyAddressRequest,
    principal: Principal?,
  ): ResponseEntity<ClassifyAddressResult> = try {
    ResponseEntity.ok(
      service.classify(request.emailAddress, request.categoryCode, request.prisonCode, principal?.name),
    )
  } catch (_: UnknownCategoryException) {
    ResponseEntity.notFound().build()
  } catch (_: IllegalArgumentException) {
    ResponseEntity.unprocessableEntity().build()
  }
}
