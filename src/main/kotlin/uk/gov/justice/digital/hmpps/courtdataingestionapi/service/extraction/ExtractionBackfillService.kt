package uk.gov.justice.digital.hmpps.courtdataingestionapi.service.extraction

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction.format.FormatModelRegistry
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.ExtractionResultRepository
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Reprocesses the back-catalogue. Backfill, re-template (new format version) and re-parse (new
 * extractor version) are the same operation: drain the documents lacking a result at the active
 * versions. At a few tens of thousands of documents this is minutes, so it is a bounded-concurrency
 * loop, not a queue fan-out. Resumable, because each pass re-queries and completed/FAILED rows drop
 * out. Safe to overlap with live traffic because ExtractionService is idempotent.
 */
@Service
class ExtractionBackfillService(
  private val repository: ExtractionResultRepository,
  private val extractionService: ExtractionService,
  private val formatModels: FormatModelRegistry,
  @Value("\${extraction.extractor-version:dev}") private val extractorVersion: String,
  @Value("\${extraction.backfill.batch-size:500}") private val batchSize: Int,
  @Value("\${extraction.backfill.concurrency:4}") private val concurrency: Int,
) {
  private val running = AtomicBoolean(false)

  /** @return false if a backfill is already running on this pod. */
  fun runBackfill(): Boolean {
    if (!running.compareAndSet(false, true)) {
      log.info("Backfill already running on this pod; ignoring request")
      return false
    }
    val model = formatModels.active()
    val pool = Executors.newFixedThreadPool(concurrency)
    val processed = AtomicInteger()
    try {
      log.info("Backfill starting for {} v{} extractor={}", model.id, model.version, extractorVersion)
      while (true) {
        val batch = repository.findDocumentIdsMissingExtraction(model.id, model.version, extractorVersion, batchSize)
        if (batch.isEmpty()) break
        val futures = batch.map { id ->
          CompletableFuture.runAsync(
            {
              runCatching { extractionService.extractAndStore(id) }
                .onFailure { log.error("Backfill failed hard for {}", id, it) }
              processed.incrementAndGet()
            },
            pool,
          )
        }
        CompletableFuture.allOf(*futures.toTypedArray()).join()
        log.info("Backfill processed {} so far", processed.get())
      }
      log.info("Backfill complete: {} documents processed", processed.get())
    } finally {
      pool.shutdown()
      running.set(false)
    }
    return true
  }

  companion object {
    private val log = LoggerFactory.getLogger(ExtractionBackfillService::class.java)
  }
}
