package uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.step

import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction.ExtractedTextNormaliser
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.IngestionContext
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.IngestionEnricher

@Component
@Order(400)
class HashExtractedText(
  private val normaliser: ExtractedTextNormaliser,
) : IngestionEnricher {
  override fun enrich(context: IngestionContext): IngestionContext {
    val text = context.extractedText ?: return context
    return context.copy(extractedTextSha256 = normaliser.normalisedHash(text))
  }
}
