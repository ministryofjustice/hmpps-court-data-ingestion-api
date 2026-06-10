package uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.BackfillRun
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.BackfillRunStatus
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.BackfillRunRepository
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

@Service
class BackfillRunner(
  private val repository: BackfillRunRepository,
  @Value("\${backfill.batch-size:200}") private val defaultBatchSize: Int,
  @Value("\${backfill.stale-heartbeat-threshold:PT5M}") staleThresholdIso: String,
) {

  private val staleThreshold: Duration = Duration.parse(staleThresholdIso)

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun acquireLock(backfillId: String, triggeredBy: String?): BackfillRun? = try {
    repository.saveAndFlush(
      BackfillRun(
        backfillId = backfillId,
        status = BackfillRunStatus.RUNNING,
        triggeredBy = triggeredBy,
      ),
    )
  } catch (_: DataIntegrityViolationException) {
    log.info("Backfill {} already running; lock not acquired", backfillId)
    null
  }

  @Async("backfillExecutor")
  fun runAsync(runId: UUID, backfill: Backfill<*>, batchSize: Int = defaultBatchSize) {
    // Capture the star-projected element type in a generic method so selectBatch() and process()
    // line up on the same T without an unchecked cast.
    runTyped(runId, backfill, batchSize)
  }

  private fun <T> runTyped(runId: UUID, backfill: Backfill<T>, batchSize: Int) {
    val pool = Executors.newFixedThreadPool(backfill.concurrency.coerceAtLeast(1))
    val processed = AtomicLong()
    val failed = AtomicLong()
    var cursor = ""
    val lastFailure = AtomicReference<Throwable?>(null)

    try {
      log.info("Backfill {} run {} starting", backfill.id, runId)
      while (true) {
        val batch = backfill.selectBatch(cursor, batchSize)
        if (batch.items.isEmpty()) break

        val futures = batch.items.map { item ->
          CompletableFuture.runAsync(
            {
              runCatching { backfill.process(item) }
                .onSuccess { processed.incrementAndGet() }
                .onFailure { ex ->
                  failed.incrementAndGet()
                  log.error("Backfill {} failed for item {}", backfill.id, item, ex)
                  lastFailure.set(ex)
                }
            },
            pool,
          )
        }
        CompletableFuture.allOf(*futures.toTypedArray()).join()

        cursor = batch.nextCursor
        heartbeat(runId, cursor, processed.get(), failed.get())
      }
      val failedCount = failed.get()
      val reason = if (failedCount > 0) {
        lastFailure.get()?.let { "$failedCount item(s) failed; sample: ${it.message ?: it.javaClass.simpleName}" }
      } else {
        null
      }
      complete(runId, BackfillRunStatus.COMPLETED, processed.get(), failedCount, reason)
      log.info("Backfill {} run {} complete: {} processed, {} failed", backfill.id, runId, processed.get(), failedCount)
    } catch (ex: Throwable) {
      log.error("Backfill {} run {} aborted", backfill.id, runId, ex)
      complete(runId, BackfillRunStatus.FAILED, processed.get(), failed.get(), ex.message ?: ex.javaClass.simpleName)
    } finally {
      pool.shutdown()
    }
  }

  @Transactional
  fun heartbeat(runId: UUID, cursor: String, processed: Long, failed: Long) {
    repository.findById(runId).ifPresent { run ->
      run.cursor = cursor
      run.processed = processed
      run.failed = failed
      run.heartbeatAt = LocalDateTime.now()
      repository.save(run)
    }
  }

  @Transactional
  fun complete(runId: UUID, status: BackfillRunStatus, processed: Long, failed: Long, reason: String?) {
    repository.findById(runId).ifPresent { run ->
      run.status = status
      run.processed = processed
      run.failed = failed
      run.completedAt = LocalDateTime.now()
      run.heartbeatAt = run.completedAt!!
      run.failureReason = reason
      repository.save(run)
    }
  }

  @Transactional
  fun sweepStaleRuns(): Int {
    val now = LocalDateTime.now()
    val threshold = now.minus(staleThreshold)
    val swept = repository.reclaimStaleRunning(threshold, now)
    if (swept > 0) log.warn("Reclaimed {} stale backfill run(s)", swept)
    return swept
  }

  companion object {
    private val log = LoggerFactory.getLogger(BackfillRunner::class.java)
  }
}
