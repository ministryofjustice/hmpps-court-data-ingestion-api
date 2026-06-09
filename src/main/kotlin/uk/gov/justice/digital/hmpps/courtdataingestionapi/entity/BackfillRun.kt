package uk.gov.justice.digital.hmpps.courtdataingestionapi.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "backfill_run")
data class BackfillRun(
  @Id
  @Column(name = "run_id")
  val runId: UUID = UUID.randomUUID(),

  @Column(name = "backfill_id")
  val backfillId: String,

  @Enumerated(EnumType.STRING)
  var status: BackfillRunStatus,

  @Column(name = "cursor")
  var cursor: String? = null,

  var processed: Long = 0,

  var failed: Long = 0,

  @Column(name = "started_at")
  val startedAt: LocalDateTime = LocalDateTime.now(),

  @Column(name = "heartbeat_at")
  var heartbeatAt: LocalDateTime = LocalDateTime.now(),

  @Column(name = "completed_at")
  var completedAt: LocalDateTime? = null,

  @Column(name = "triggered_by")
  val triggeredBy: String? = null,

  @Column(name = "failure_reason")
  var failureReason: String? = null,
)

enum class BackfillRunStatus { RUNNING, COMPLETED, FAILED }
