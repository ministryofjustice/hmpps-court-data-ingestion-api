package uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion

import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.courtdataingestionapi.util.Sha256

@Component
@Order(400)
class HashExtractedText : IngestionEnricher {
  override fun enrich(context: IngestionContext): IngestionContext {
    val text = context.extractedText ?: return context
    return context.copy(extractedTextSha256 = Sha256.hex(text.toByteArray(Charsets.UTF_8)))
  }
}
