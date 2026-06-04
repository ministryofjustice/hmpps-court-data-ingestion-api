package uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion

import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.courtdataingestionapi.util.Sha256

@Component
@Order(200)
class HashDownloadedFile : IngestionEnricher {
  override fun enrich(context: IngestionContext): IngestionContext {
    val bytes = context.downloadedFileBytes ?: return context
    return context.copy(downloadedFileSha256 = Sha256.hex(bytes))
  }
}
