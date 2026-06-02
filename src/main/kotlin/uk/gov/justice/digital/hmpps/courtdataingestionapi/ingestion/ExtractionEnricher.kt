package uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion

import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.extraction.ExtractionService

@Component
@Order(200)
class ExtractionEnricher(
  private val extractionService: ExtractionService,
) : IngestionEnricher {
  companion object {
    private val log = org.slf4j.LoggerFactory.getLogger(this::class.java)
  }

  override fun enrich(context: IngestionContext): IngestionContext {
    val prisonDocumentId = context.prisonDocumentId ?: return context

    runCatching {
      extractionService.extractAndStore(prisonDocumentId)
    }.onFailure {
      log.warn("Extraction skipped for document {}", prisonDocumentId, it)
    }

    return context
  }
}
