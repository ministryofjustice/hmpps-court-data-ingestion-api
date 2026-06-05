package uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.step

import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.IngestionContext
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.IngestionEnricher
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.ingestion.DuplicateResolutionService

@Component
@Order(600)
class ResolveDuplicates(
  private val duplicateResolutionService: DuplicateResolutionService,
) : IngestionEnricher {

  override fun enrich(context: IngestionContext): IngestionContext {
    val prisonDocumentId = context.prisonDocumentId ?: return context

    if (context.downloadedFileSha256 == null && context.extractedTextSha256 == null) {
      return context.copy(
        warnings = context.warnings + "Duplicate resolution skipped because no hashes were available",
      )
    }

    val outcome = duplicateResolutionService.resolve(
      currentDocumentId = prisonDocumentId,
      downloadedFileSha256 = context.downloadedFileSha256,
      extractedTextSha256 = context.extractedTextSha256,
    ) ?: return context

    return context.copy(
      duplicateOf = outcome.duplicateOf,
      warnings = outcome.reason?.let { context.warnings + it } ?: context.warnings,
    )
  }
}
