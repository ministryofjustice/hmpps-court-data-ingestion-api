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
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

@Service
class BackfillRunner(
  private val repository: BackfillRunRepository,
  private val store: BackfillRunStore,
  @Value("\${extraction.backfill.stale-heartbeat-threshold:PT5M}") staleThresholdIso: String,
  @Value("\${extraction.backfill.heartbeat-interval:PT30S}") heartbeatIntervalIso: String,
) {

  private val staleThreshold: Duration = Duration.parse(staleThresholdIso)
  private val heartbeatInterval: Duration = Duration.parse(heartbeatIntervalIso)

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
  fun runAsync(runId: UUID, backfill: Backfill<*>) {
    runTyped(runId, backfill, BATCH_SIZE)
  }

  private fun <T> runTyped(runId: UUID, backfill: Backfill<T>, batchSize: Int) {
    if (batchSize <= 0) {
      val msg = "batch size must be positive but was $batchSize (check extraction.backfill.batch-size / BACKFILL env)"
      log.error("Backfill {} run {} not started: {}", backfill.id, runId, msg)
      store.finish(runId, BackfillRunStatus.FAILED, 0, 0, msg)
      return
    }
    val pool = Executors.newFixedThreadPool(backfill.concurrency.coerceAtLeast(1))
    val heartbeatPool = Executors.newSingleThreadScheduledExecutor { r ->
      Thread(r, "backfill-heartbeat-$runId").apply { isDaemon = true }
    }
    val processed = AtomicLong()
    val failed = AtomicLong()
    val cursor = AtomicReference("")
    val lastFailure = AtomicReference<Throwable?>(null)
    val reclaimed = AtomicBoolean(false)
    val beat = heartbeatPool.scheduleAtFixedRate(
      {
        runCatching { store.heartbeat(runId, cursor.get(), processed.get(), failed.get()) }
          .onSuccess { stillOwned -> if (!stillOwned) reclaimed.set(true) }
          .onFailure { ex -> log.warn("Backfill {} run {} heartbeat write failed", backfill.id, runId, ex) }
      },
      heartbeatInterval.toMillis(),
      heartbeatInterval.toMillis(),
      TimeUnit.MILLISECONDS,
    )

    try {
      log.info("Backfill {} run {} starting", backfill.id, runId)
      while (!reclaimed.get()) {
        val batch = backfill.selectBatch(cursor.get(), batchSize)
        if (batch.items.isEmpty()) break

        val futures = batch.items.map { item ->
          CompletableFuture.runAsync(
            {
              if (reclaimed.get()) return@runAsync
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
        cursor.set(batch.nextCursor)
      }

      if (reclaimed.get()) {
        log.warn(
          "Backfill {} run {} aborting: lock reclaimed as stale; {} processed, {} failed before abort",
          backfill.id,
          runId,
          processed.get(),
          failed.get(),
        )
        return
      }

      val failedCount = failed.get()
      val reason = if (failedCount > 0) {
        lastFailure.get()?.let { "$failedCount item(s) failed; sample: ${it.message ?: it.javaClass.simpleName}" }
      } else {
        null
      }
      if (store.finish(runId, BackfillRunStatus.COMPLETED, processed.get(), failedCount, reason)) {
        log.info("Backfill {} run {} complete: {} processed, {} failed", backfill.id, runId, processed.get(), failedCount)
      } else {
        log.warn("Backfill {} run {} finished but row was no longer RUNNING; not overwriting", backfill.id, runId)
      }
    } catch (ex: Throwable) {
      log.error("Backfill {} run {} aborted", backfill.id, runId, ex)
      store.finish(runId, BackfillRunStatus.FAILED, processed.get(), failed.get(), ex.message ?: ex.javaClass.simpleName)
    } finally {
      beat.cancel(true)
      heartbeatPool.shutdownNow()
      pool.shutdown()
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
    private const val BATCH_SIZE = 200
    private val log = LoggerFactory.getLogger(BackfillRunner::class.java)
  }
}
