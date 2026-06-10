package uk.gov.justice.digital.hmpps.courtdataingestionapi.controller

import org.springframework.boot.actuate.endpoint.annotation.Endpoint
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation
import org.springframework.boot.actuate.endpoint.annotation.Selector
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation
import org.springframework.lang.Nullable
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill.BackfillRegistry
import uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill.BackfillRunner
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.BackfillRun
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.BackfillRunRepository
import java.time.LocalDateTime
import java.util.UUID

@Component
@Endpoint(id = "backfill")
class BackfillEndpoint(
  private val registry: BackfillRegistry,
  private val runner: BackfillRunner,
  private val runRepository: BackfillRunRepository,
) {

  @WriteOperation
  fun trigger(id: String, @Nullable triggeredBy: String?): TriggerResponse {
    val backfill = registry.get(id) ?: return TriggerResponse(
      status = "unknown-backfill",
      message = "No backfill registered for id '$id'. Known ids: ${registry.ids().sorted()}",
    )
    val run = runner.acquireLock(id, triggeredBy)
      ?: return TriggerResponse(status = "already-running", message = "A run for '$id' is in flight")
    runner.runAsync(run.runId, backfill)
    return TriggerResponse(
      status = "started",
      runId = run.runId,
      message = "Backfill '$id' started",
    )
  }

  @ReadOperation
  fun mostRecent(@Selector id: String): StatusResponse = runRepository.findFirstByBackfillIdOrderByStartedAtDesc(id)?.toStatus()
    ?: StatusResponse(backfillId = id, status = "no-runs")

  @ReadOperation
  fun list(): ListResponse = ListResponse(
    registered = registry.ids().sorted(),
    recent = registry.ids().mapNotNull { id ->
      runRepository.findFirstByBackfillIdOrderByStartedAtDesc(id)?.toStatus()
    },
  )

  private fun BackfillRun.toStatus() = StatusResponse(
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

  data class TriggerResponse(
    val status: String,
    val runId: UUID? = null,
    val message: String,
  )

  data class StatusResponse(
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

  data class ListResponse(
    val registered: List<String>,
    val recent: List<StatusResponse>,
  )
}

@Component
@Endpoint(id = "hashbackfill")
class HashBackfillCompatEndpoint(private val endpoint: BackfillEndpoint) {
  @WriteOperation
  fun trigger(@Nullable triggeredBy: String?): BackfillEndpoint.TriggerResponse = endpoint.trigger("hash", triggeredBy)
}

@Component
@Endpoint(id = "extractionbackfill")
class ExtractionBackfillCompatEndpoint(private val endpoint: BackfillEndpoint) {
  @WriteOperation
  fun trigger(@Nullable triggeredBy: String?): BackfillEndpoint.TriggerResponse = endpoint.trigger("extraction", triggeredBy)
}
