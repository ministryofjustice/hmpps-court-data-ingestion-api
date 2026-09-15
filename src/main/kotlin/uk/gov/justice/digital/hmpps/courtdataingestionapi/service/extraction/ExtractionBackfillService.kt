package uk.gov.justice.digital.hmpps.courtdataingestionapi.service.extraction

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction.format.FormatModelRegistry
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.IngestionContext
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.IngestionEnrichmentFlow
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.applyEnrichment
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtDocumentRepository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.ExtractionResultRepository
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@Service
class ExtractionBackfillService(
  private val repository: ExtractionResultRepository,
  private val courtDocumentRepository: CourtDocumentRepository,
  private val ingestionEnrichmentFlow: IngestionEnrichmentFlow,
  private val extractionService: ExtractionService,
  private val formatModels: FormatModelRegistry,
  private val fileService: uk.gov.justice.digital.hmpps.courtdataingestionapi.service.FileService,
  @Value("\${extraction.extractor-version:dev}") private val extractorVersion: String,
  @Value("\${extraction.backfill.batch-size:500}") private val batchSize: Int,
  @Value("\${extraction.backfill.concurrency:4}") private val concurrency: Int,
  @Value("\${extraction.backfill.ingested-from:1970-01-01T00:00:00Z}") ingestedFromIso: String,
  @Value("\${extraction.backfill.error-cooldown:PT3H}") errorCooldownIso: String,

) {
  private val running = AtomicBoolean(false)
  private val hashBackfillRunning = AtomicBoolean(false)
  private val ingestedFrom: Instant = Instant.parse(ingestedFromIso)
  private val errorCooldown: Duration = Duration.parse(errorCooldownIso)

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
        val batch = repository.findDocumentIdsMissingExtraction(
          model.id,
          model.version,
          extractorVersion,
          ingestedFrom,
          Instant.now().minus(errorCooldown),
          batchSize,
        )
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

  fun runHashBackfill(): Boolean {
    if (!hashBackfillRunning.compareAndSet(false, true)) {
      log.info("Hash backfill already running on this pod; ignoring request")
      return false
    }
    var afterId = ZERO_UUID
    var processed = 0

    try {
      log.info("Hash backfill starting")
      while (true) {
        val batch = courtDocumentRepository.findUnhashedAfter(afterId, batchSize)
        if (batch.isEmpty()) break

        batch.forEach { document ->
          runCatching {
            val enriched = ingestionEnrichmentFlow.runForBackfill(
              IngestionContext(
                prisonEmailAddress = document.prisonEmailAddress,
                prisonDocumentId = document.prisonDocumentId,
              ),
            )
            courtDocumentRepository.save(document.applyEnrichment(enriched))
            runCatching {
              fileService.mirrorEnrichmentToDocumentStore(document)
            }.onFailure { log.warn("Hash backfill mirror to document store failed for {}", document.id, it) }
          }.onFailure { log.error("Hash backfill failed for {}", document.id, it) }
        }

        afterId = batch.last().id
        processed += batch.size
        log.info("Hash backfill processed {} so far", processed)
      }
      log.info("Hash backfill complete: {} documents processed", processed)
    } finally {
      hashBackfillRunning.set(false)
    }
    return true
  }

  companion object {
    private val log = LoggerFactory.getLogger(ExtractionBackfillService::class.java)
    private val ZERO_UUID = UUID(0L, 0L)
  }
}
