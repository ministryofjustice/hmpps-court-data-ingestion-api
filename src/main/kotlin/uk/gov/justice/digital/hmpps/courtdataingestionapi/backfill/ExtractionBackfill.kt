package uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction.format.FormatModelRegistry
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.ExtractionResultRepository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.extraction.ExtractionService
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Component
@ConditionalOnProperty(
  prefix = "extraction.structured",
  name = ["enabled"],
  havingValue = "true",
  matchIfMissing = false,
)
class ExtractionBackfill(
  private val repository: ExtractionResultRepository,
  private val extractionService: ExtractionService,
  private val formatModels: FormatModelRegistry,
  @Value("\${extraction.extractor-version:dev}") private val extractorVersion: String,
  @Value("\${extraction.backfill.ingested-from:1970-01-01T00:00:00Z}") ingestedFromIso: String,
  @Value("\${extraction.backfill.error-cooldown:PT3H}") errorCooldownIso: String,
) : Backfill<UUID> {

  override val id = "extraction"
  override val concurrency = 4

  private val ingestedFrom: Instant = Instant.parse(ingestedFromIso)
  private val errorCooldown: Duration = Duration.parse(errorCooldownIso)

  override fun selectBatch(cursor: String, batchSize: Int): BackfillBatch<UUID> {
    val model = formatModels.active()
    val ids = repository.findDocumentIdsMissingExtraction(
      model.id,
      model.version,
      extractorVersion,
      ingestedFrom,
      Instant.now().minus(errorCooldown),
      batchSize,
    )
    return BackfillBatch(ids, cursor)
  }

  override fun process(item: UUID) {
    extractionService.extractAndStore(item)
  }
}
