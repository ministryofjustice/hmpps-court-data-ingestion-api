package uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.courtdataingestionapi.util.Sha256

class ExtractedTextNormaliserTest {

  @Test
  fun `with no patterns configured, hash matches a plain sha256 of the text`() {
    val normaliser = ExtractedTextNormaliser(ContentNormalisationProperties(patterns = emptyList()))

    val hash = normaliser.normalisedHash("some text")

    assertThat(hash).isEqualTo(Sha256.hex("some text".toByteArray(Charsets.UTF_8)))
  }

  @Test
  fun `a single matching pattern is stripped before hashing`() {
    val normaliser = ExtractedTextNormaliser(
      ContentNormalisationProperties(patterns = listOf("Register generated on: \\d{2}/\\d{2}/\\d{4}")),
    )

    val withDateA = normaliser.normalisedHash("Register generated on: 17/06/2026\nRest of document")
    val withDateB = normaliser.normalisedHash("Register generated on: 24/06/2026\nRest of document")

    assertThat(withDateA).isEqualTo(withDateB)
  }

  @Test
  fun `text with no match is unaffected by an unrelated pattern`() {
    val normaliser = ExtractedTextNormaliser(
      ContentNormalisationProperties(patterns = listOf("Register generated on: \\d{2}/\\d{2}/\\d{4}")),
    )

    val hash = normaliser.normalisedHash("nothing volatile here")

    assertThat(hash).isEqualTo(Sha256.hex("nothing volatile here".toByteArray(Charsets.UTF_8)))
  }

  @Test
  fun `multiple configured patterns are all applied`() {
    val normaliser = ExtractedTextNormaliser(
      ContentNormalisationProperties(
        patterns = listOf(
          "Register generated on: \\d{2}/\\d{2}/\\d{4}",
          "Printed at \\d{2}:\\d{2}",
        ),
      ),
    )

    val a = normaliser.normalisedHash("Register generated on: 17/06/2026\nPrinted at 09:14\nbody")
    val b = normaliser.normalisedHash("Register generated on: 24/06/2026\nPrinted at 22:57\nbody")

    assertThat(a).isEqualTo(b)
  }

  @Test
  fun `a pattern occurring on every page is stripped everywhere it appears`() {
    val normaliser = ExtractedTextNormaliser(
      ContentNormalisationProperties(patterns = listOf("Register generated on: \\d{2}/\\d{2}/\\d{4}")),
    )

    val page1And2 = "Register generated on: 17/06/2026\npage one\nRegister generated on: 17/06/2026\npage two"
    val regeneratedPage1And2 = "Register generated on: 24/06/2026\npage one\nRegister generated on: 24/06/2026\npage two"

    assertThat(normaliser.normalisedHash(page1And2)).isEqualTo(normaliser.normalisedHash(regeneratedPage1And2))
  }

  @Test
  fun `changing substantive content still changes the hash even when the volatile line also changes`() {
    val normaliser = ExtractedTextNormaliser(
      ContentNormalisationProperties(patterns = listOf("Register generated on: \\d{2}/\\d{2}/\\d{4}")),
    )

    val original = normaliser.normalisedHash("Register generated on: 17/06/2026\nCase reference ABC123")
    val corrected = normaliser.normalisedHash("Register generated on: 24/06/2026\nCase reference XYZ999")

    assertThat(original).isNotEqualTo(corrected)
  }
}
