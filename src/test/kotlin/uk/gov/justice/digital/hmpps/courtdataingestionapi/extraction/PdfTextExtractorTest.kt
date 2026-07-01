package uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PdfTextExtractorTest {

  private val extractor = PdfTextExtractor()

  @Test
  fun `extracts text from an exemplar court register PDF`() {
    val text = extractor.extractText(readFixtureBytes("example-register.pdf"))

    assertThat(text).isNotNull()
    assertThat(text).contains("Register generated on: 17/06/2026")
  }

  @Test
  fun `extracts text from the existing moriarty fixture too`() {
    val text = extractor.extractText(readFixtureBytes("moriarty-register.pdf"))

    assertThat(text).isNotNull()
    assertThat(text).contains("MORIARTY")
  }

  @Test
  fun `returns null for bytes with no PDF header`() {
    val text = extractor.extractText("not a pdf".toByteArray())

    assertThat(text).isNull()
  }

  @Test
  fun `returns null for empty bytes`() {
    val text = extractor.extractText(ByteArray(0))

    assertThat(text).isNull()
  }

  @Test
  fun `returns null for bytes too short to contain a header`() {
    val text = extractor.extractText(byteArrayOf('%'.code.toByte(), 'P'.code.toByte()))

    assertThat(text).isNull()
  }

  @Test
  fun `returns null for a PDF header followed by garbage that fails to parse`() {
    val text = extractor.extractText("%PDF-1.3 not actually a valid pdf body".toByteArray())

    assertThat(text).isNull()
  }

  private fun readFixtureBytes(name: String): ByteArray {
    val stream = checkNotNull(javaClass.getResourceAsStream("/test-fixtures/$name")) {
      "Test fixture PDF not found on classpath at /test-fixtures/$name"
    }
    return stream.use { it.readBytes() }
  }
}
