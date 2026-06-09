package uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.step

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.IngestionContext
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.TestPdfFactory

class ExtractPdfTextTest {

  private val enricher = ExtractPdfText()

  @Test
  fun `extracts text from a pdf`() {
    val bytes = TestPdfFactory.singlePagePdf("WARRANT TEXT")
    val input = IngestionContext(
      prisonEmailAddress = null,
      prisonDocumentId = null,
      downloadedFileBytes = bytes,
      hearingId = null,
      caseReferences = null,
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
      hearingId = null,
      caseReferences = null,
    )

    val result = enricher.enrich(input)

    assertThat(result.extractedText).isNull()
    assertThat(result.warnings).isNotEmpty
  }

  @Test
  fun `skips when no bytes present`() {
    val input = IngestionContext(null, null, hearingId = null, caseReferences = null)

    val result = enricher.enrich(input)

    assertThat(result).isEqualTo(input)
  }
}
