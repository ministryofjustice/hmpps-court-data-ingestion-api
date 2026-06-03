package uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion

import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import java.security.MessageDigest

@Component
@Order(200)
class HashDownloadedFileEnricher : IngestionEnricher {
  override fun enrich(context: IngestionContext): IngestionContext {
    val bytes = context.downloadedFileBytes ?: return context
    return context.copy(downloadedFileSha256 = sha256(bytes))
  }

  private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes)
    .joinToString("") { "%02x".format(it) }
}
