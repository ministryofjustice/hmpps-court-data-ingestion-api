package uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion

import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import java.security.MessageDigest

@Component
@Order(400)
class HashExtractedTextEnricher : IngestionEnricher {
  override fun enrich(context: IngestionContext): IngestionContext {
    val text = context.extractedText ?: return context
    return context.copy(extractedTextSha256 = sha256(text.toByteArray(Charsets.UTF_8)))
  }

  private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes)
    .joinToString("") { "%02x".format(it) }
}
