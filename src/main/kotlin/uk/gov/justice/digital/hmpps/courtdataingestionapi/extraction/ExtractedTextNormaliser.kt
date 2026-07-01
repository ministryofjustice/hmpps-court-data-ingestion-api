package uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.courtdataingestionapi.util.Sha256

@Component
class ExtractedTextNormaliser(
  normalisationProperties: ContentNormalisationProperties,
) {
  private val patterns = normalisationProperties.patterns.map { it.toRegex() }

  fun getNormalisedHash(text: String): String {
    val normalised = patterns.fold(text) { acc, pattern -> pattern.replace(acc, "") }
    return Sha256.hex(normalised.toByteArray(Charsets.UTF_8))
  }
}
