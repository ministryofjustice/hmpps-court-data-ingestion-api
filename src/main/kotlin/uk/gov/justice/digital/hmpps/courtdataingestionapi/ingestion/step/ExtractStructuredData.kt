package uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.step

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.IngestionContext
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.IngestionEnricher
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.extraction.ExtractionService

@Component
@Order(700)
@ConditionalOnProperty(
  prefix = "extraction.structured",
  name = ["enabled"],
  havingValue = "true",
  matchIfMissing = false,
)
class ExtractStructuredData(
  private val extractionService: ExtractionService,
) : IngestionEnricher {

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  override fun enrich(context: IngestionContext): IngestionContext {
    val prisonDocumentId = context.prisonDocumentId ?: return context
    val extractedText = context.extractedText

    if (extractedText.isNullOrBlank()) {
      return context.copy(
        warnings = context.warnings + "Structured extraction skipped: extracted text is empty",
      )
    }

    runCatching {
      extractionService.extractStructuredDataAndStore(
        documentId = prisonDocumentId,
        extractedText = extractedText,
        downloadedFileSha256 = context.downloadedFileSha256,
      )
    }.onFailure {
      log.warn("Structured extraction skipped for document {}", prisonDocumentId, it)
    }

    return context
  }
}
