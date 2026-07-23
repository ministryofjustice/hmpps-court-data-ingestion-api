package uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.BackfillRunStatus
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.BackfillRunRepository
import java.time.LocalDateTime
import java.util.UUID

@Component
class BackfillRunStore(
  private val repository: BackfillRunRepository,
) {

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun heartbeat(runId: UUID, cursor: String?, processed: Long, failed: Long): Boolean = repository.touchHeartbeat(runId, LocalDateTime.now(), cursor, processed, failed) == 1

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun finish(runId: UUID, status: BackfillRunStatus, processed: Long, failed: Long, reason: String?): Boolean = repository.finishIfRunning(runId, status, processed, failed, LocalDateTime.now(), reason) == 1
}
