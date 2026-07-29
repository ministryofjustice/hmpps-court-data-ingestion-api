package uk.gov.justice.digital.hmpps.courtdataingestionapi.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill.BackfillRegistry
import uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill.BackfillRunner
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.BackfillRun
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.BackfillRunRepository
import java.security.Principal
import java.time.LocalDateTime
import java.util.UUID

/**
 * Authenticated equivalent of [BackfillEndpoint], for use by the support dashboard in
 * hmpps-court-cases-release-dates.
 *
 * [BackfillEndpoint] stays as it is. It is an actuator endpoint on an unauthenticated path
 * that is blocked at the ingress, so it is only reachable via `kubectl port-forward`. That is
 * fine for hands on operator use but unusable from another service.
 *
 * This controller sits on a normal, ingress reachable path and is authorised on the caller's
 * own token. Callers must present a user token holding ROLE_COURTCASE_RELEASEDATE_SUPPORT, so
 * authorisation is enforced here rather than being delegated to the calling frontend, and
 * every run is attributable to a named person.
 */
@RestController
@RequestMapping("/admin/backfill", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "BackfillAdminController", description = "Support operations for triggering and monitoring backfills")
@PreAuthorize("hasRole('COURTCASE_RELEASEDATE_SUPPORT')")
class BackfillAdminController(
  private val registry: BackfillRegistry,
  private val runner: BackfillRunner,
  private val runRepository: BackfillRunRepository,
) {

  @GetMapping
  @Operation(
    summary = "List registered backfills with their most recent run",
    description = "Returns every backfill id known to the registry, plus the latest run for each that has one.",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Successfully returns the backfill list"),
      ApiResponse(responseCode = "401", description = "Unauthorized - valid Oauth2 token required"),
      ApiResponse(responseCode = "403", description = "Forbidden - requires ROLE_COURTCASE_RELEASEDATE_SUPPORT"),
    ],
  )
  fun list(): BackfillListResponse = BackfillListResponse(
    registered = registry.ids().sorted(),
    recent = registry.ids().mapNotNull { id ->
      runRepository.findFirstByBackfillIdOrderByStartedAtDesc(id)?.toResponse()
    },
  )

  @GetMapping("/{backfillId}")
  @Operation(summary = "Retrieve the most recent run for a single backfill")
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "Successfully returns the most recent run"),
      ApiResponse(responseCode = "401", description = "Unauthorized - valid Oauth2 token required"),
      ApiResponse(responseCode = "403", description = "Forbidden - requires ROLE_COURTCASE_RELEASEDATE_SUPPORT"),
      ApiResponse(responseCode = "404", description = "No backfill is registered with that id"),
    ],
  )
  fun mostRecent(
    @Parameter(required = true, example = "extraction", description = "Registered backfill id")
    @PathVariable backfillId: String,
  ): ResponseEntity<BackfillRunResponse> {
    if (registry.get(backfillId) == null) return ResponseEntity.notFound().build()

    val response = runRepository.findFirstByBackfillIdOrderByStartedAtDesc(backfillId)?.toResponse()
      ?: BackfillRunResponse(backfillId = backfillId, status = NO_RUNS)

    return ResponseEntity.ok(response)
  }

  @PostMapping("/{backfillId}")
  @Operation(
    summary = "Start a backfill",
    description = "Acquires the run lock and starts the backfill asynchronously. " +
      "Returns 409 if a run for that backfill is already in flight.",
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "202", description = "Backfill accepted and started"),
      ApiResponse(responseCode = "401", description = "Unauthorized - valid Oauth2 token required"),
      ApiResponse(responseCode = "403", description = "Forbidden - requires ROLE_COURTCASE_RELEASEDATE_SUPPORT"),
      ApiResponse(responseCode = "404", description = "No backfill is registered with that id"),
      ApiResponse(responseCode = "409", description = "A run for that backfill is already in flight"),
    ],
  )
  fun start(
    @Parameter(required = true, example = "extraction", description = "Registered backfill id")
    @PathVariable backfillId: String,
    principal: Principal?,
  ): ResponseEntity<TriggerResponse> {
    val backfill = registry.get(backfillId)
      ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
        TriggerResponse(message = "No backfill registered for id '$backfillId'"),
      )

    val triggeredBy = principal?.name ?: "unknown"

    val run = runner.acquireLock(backfillId, triggeredBy)
      ?: return ResponseEntity.status(HttpStatus.CONFLICT).body(
        TriggerResponse(message = "A run for '$backfillId' is already in flight"),
      )

    log.info("Backfill {} run {} started by {}", backfillId, run.runId, triggeredBy)
    runner.runAsync(run.runId, backfill)

    return ResponseEntity.status(HttpStatus.ACCEPTED).body(
      TriggerResponse(runId = run.runId, message = "Backfill '$backfillId' started"),
    )
  }

  private fun BackfillRun.toResponse() = BackfillRunResponse(
    backfillId = backfillId,
    runId = runId,
    status = status.name,
    cursor = cursor,
    processed = processed,
    failed = failed,
    startedAt = startedAt,
    heartbeatAt = heartbeatAt,
    completedAt = completedAt,
    triggeredBy = triggeredBy,
    failureReason = failureReason,
  )

  data class BackfillRunResponse(
    val backfillId: String,
    val runId: UUID? = null,
    val status: String,
    val cursor: String? = null,
    val processed: Long = 0,
    val failed: Long = 0,
    val startedAt: LocalDateTime? = null,
    val heartbeatAt: LocalDateTime? = null,
    val completedAt: LocalDateTime? = null,
    val triggeredBy: String? = null,
    val failureReason: String? = null,
  )

  data class BackfillListResponse(
    val registered: List<String>,
    val recent: List<BackfillRunResponse>,
  )

  data class TriggerResponse(
    val runId: UUID? = null,
    val message: String,
  )

  companion object {
    const val NO_RUNS = "NO_RUNS"
    private val log: Logger = LoggerFactory.getLogger(BackfillAdminController::class.java)
  }
}
