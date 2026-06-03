package uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ExtractPdfTextEnricherTest {

  private val enricher = ExtractPdfTextEnricher()

  @Test
  fun `extracts text from a pdf`() {
    val bytes = TestPdfFactory.singlePagePdf("WARRANT TEXT")
    val input = IngestionContext(
      prisonEmailAddress = null,
      prisonDocumentId = null,
      downloadedFileBytes = bytes,
    )

    val result = enricher.enrich(input)

    assertThat(result.extractedText).contains("WARRANT TEXT")
  }

  @Test
  fun `adds warning when file is not pdf`() {
    val input = IngestionContext(
      prisonEmailAddress = null,
      prisonDocumentId = null,
      downloadedFileBytes = "not-a-pdf".toByteArray(),
    )

    val result = enricher.enrich(input)

    assertThat(result.extractedText).isNull()
    assertThat(result.warnings).isNotEmpty
  }

  @Test
  fun `skips when no bytes present`() {
    val input = IngestionContext(null, null)

    val result = enricher.enrich(input)

    assertThat(result).isEqualTo(input)
  }
}
