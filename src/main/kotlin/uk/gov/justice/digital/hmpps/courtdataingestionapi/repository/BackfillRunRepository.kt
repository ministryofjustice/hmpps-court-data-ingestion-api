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
}
