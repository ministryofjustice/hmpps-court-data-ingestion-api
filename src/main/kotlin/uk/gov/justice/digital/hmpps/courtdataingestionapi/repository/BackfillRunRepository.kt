package uk.gov.justice.digital.hmpps.courtdataingestionapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.BackfillRun
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.BackfillRunStatus
import java.time.LocalDateTime
import java.util.UUID

@Repository
interface BackfillRunRepository : JpaRepository<BackfillRun, UUID> {

  fun findFirstByBackfillIdOrderByStartedAtDesc(backfillId: String): BackfillRun?

  fun findFirstByBackfillIdAndStatusOrderByStartedAtDesc(
    backfillId: String,
    status: BackfillRunStatus,
  ): BackfillRun?

  @Modifying
  @Query(
    """
      UPDATE BackfillRun b
         SET b.status = uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.BackfillRunStatus.FAILED,
             b.failureReason = 'Stale lock reclaimed: heartbeat older than threshold',
             b.completedAt = :now
       WHERE b.status = uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.BackfillRunStatus.RUNNING
         AND b.heartbeatAt < :threshold
    """,
  )
  fun reclaimStaleRunning(
    @Param("threshold") threshold: LocalDateTime,
    @Param("now") now: LocalDateTime,
  ): Int

  @Modifying
  @Query(
    """
      UPDATE BackfillRun b
         SET b.heartbeatAt = :now,
             b.cursor = :cursor,
             b.processed = :processed,
             b.failed = :failed
       WHERE b.runId = :runId
         AND b.status = uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.BackfillRunStatus.RUNNING
    """,
  )
  fun touchHeartbeat(
    @Param("runId") runId: UUID,
    @Param("now") now: LocalDateTime,
    @Param("cursor") cursor: String?,
    @Param("processed") processed: Long,
    @Param("failed") failed: Long,
  ): Int

  @Modifying
  @Query(
    """
      UPDATE BackfillRun b
         SET b.status = :status,
             b.processed = :processed,
             b.failed = :failed,
             b.completedAt = :now,
             b.heartbeatAt = :now,
             b.failureReason = :reason
       WHERE b.runId = :runId
         AND b.status = uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.BackfillRunStatus.RUNNING
    """,
  )
  fun finishIfRunning(
    @Param("runId") runId: UUID,
    @Param("status") status: BackfillRunStatus,
    @Param("processed") processed: Long,
    @Param("failed") failed: Long,
    @Param("now") now: LocalDateTime,
    @Param("reason") reason: String?,
  ): Int
}
