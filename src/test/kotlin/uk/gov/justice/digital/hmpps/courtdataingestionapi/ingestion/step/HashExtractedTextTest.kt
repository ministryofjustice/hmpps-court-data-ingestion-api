package uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.step

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction.ContentNormalisationProperties
import uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction.ExtractedTextNormaliser
import uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction.PdfTextExtractor
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.IngestionContext
import uk.gov.justice.digital.hmpps.courtdataingestionapi.util.Sha256

private const val REGISTER_GENERATED_ON_PATTERN = "Register generated on: \\d{2}/\\d{2}/\\d{4}"

class HashExtractedTextTest {

  private val noPatternsEnricher = HashExtractedText(ExtractedTextNormaliser(ContentNormalisationProperties()))
  private val registerPatternEnricher = HashExtractedText(
    ExtractedTextNormaliser(ContentNormalisationProperties(patterns = listOf(REGISTER_GENERATED_ON_PATTERN))),
  )

  @Test
  fun `hashes extracted text when no normalisation patterns configured`() {
    val input = context(extractedText = "hello text")

    val result = noPatternsEnricher.enrich(input)

    assertThat(result.extractedTextSha256).isEqualTo(Sha256.hex("hello text".toByteArray(Charsets.UTF_8)))
  }

  @Test
  fun `skips when no extracted text present`() {
    val input = context(extractedText = null)

    val result = noPatternsEnricher.enrich(input)

    assertThat(result).isEqualTo(input)
  }

  @Test
  fun `unrelated text is unaffected by the register-generated-on pattern`() {
    val input = context(extractedText = "hello text")

    val result = registerPatternEnricher.enrich(input)

    assertThat(result.extractedTextSha256).isEqualTo(Sha256.hex("hello text".toByteArray(Charsets.UTF_8)))
  }

  @Test
  fun `a document differing only by generation date hashes the same once normalised, but not before`() {
    val first = context(extractedText = "Sheffield Crown Court\nRegister generated on: 17/06/2026\nCase reference ABC123")
    val regenerated = context(extractedText = "Sheffield Crown Court\nRegister generated on: 24/06/2026\nCase reference ABC123")

    val normalisedFirst = registerPatternEnricher.enrich(first)
    val normalisedRegenerated = registerPatternEnricher.enrich(regenerated)
    assertThat(normalisedFirst.extractedTextSha256).isEqualTo(normalisedRegenerated.extractedTextSha256)

    val rawFirst = noPatternsEnricher.enrich(first)
    val rawRegenerated = noPatternsEnricher.enrich(regenerated)
    assertThat(rawFirst.extractedTextSha256).isNotEqualTo(rawRegenerated.extractedTextSha256)
  }

  @Test
  fun `a substantive change still produces a different hash even with normalisation applied`() {
    val first = context(extractedText = "Register generated on: 17/06/2026\nCase reference ABC123")
    val differentCase = context(extractedText = "Register generated on: 17/06/2026\nCase reference XYZ999")

    val result1 = registerPatternEnricher.enrich(first)
    val result2 = registerPatternEnricher.enrich(differentCase)

    assertThat(result1.extractedTextSha256).isNotEqualTo(result2.extractedTextSha256)
  }

  @Test
  fun `real PCR regeneration collapses to the same hash once normalised`() {
    val extractor = PdfTextExtractor()
    val text = extractor.extractText(readFixtureBytes("example-register.pdf"))
    checkNotNull(text) { "Fixture PDF failed to extract text" }
    assertThat(text).contains("Register generated on: 17/06/2026")

    // Simulate a regeneration a week later where nothing substantive changed, exactly the
    // real-world case reported: same register, only the generated-on date moved on.
    val regeneratedText = text.replace("17/06/2026", "24/06/2026")
    assertThat(regeneratedText).isNotEqualTo(text)

    val first = registerPatternEnricher.enrich(context(extractedText = text))
    val regenerated = registerPatternEnricher.enrich(context(extractedText = regeneratedText))

    assertThat(first.extractedTextSha256).isEqualTo(regenerated.extractedTextSha256)

    // and the pre-fix behaviour, to document why this change exists
    val firstRaw = noPatternsEnricher.enrich(context(extractedText = text))
    val regeneratedRaw = noPatternsEnricher.enrich(context(extractedText = regeneratedText))
    assertThat(firstRaw.extractedTextSha256).isNotEqualTo(regeneratedRaw.extractedTextSha256)
  }

  private fun context(extractedText: String?) = IngestionContext(
    prisonEmailAddress = null,
    prisonDocumentId = null,
    extractedText = extractedText,
  )

  private fun readFixtureBytes(name: String): ByteArray {
    val stream = checkNotNull(javaClass.getResourceAsStream("/test-fixtures/$name")) {
      "Test fixture PDF not found on classpath at /test-fixtures/$name"
    }
    return stream.use { it.readBytes() }
  }
}
