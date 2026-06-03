package uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class IngestionPipelineSmokeTest {

  @Test
  fun `threads file hash text extraction and text hash across a simple pipeline`() {
    val flow = IngestionEnrichmentFlow(
      listOf(
        HashDownloadedFileEnricher(),
        ExtractPdfTextEnricher(),
        HashExtractedTextEnricher(),
      ),
    )

    val result = flow.run(
      IngestionContext(
        prisonEmailAddress = null,
        prisonDocumentId = null,
        downloadedFileBytes = TestPdfFactory.singlePagePdf("HELLO WARRANT"),
      ),
    )

    assertThat(result.downloadedFileSha256).isNotBlank()
    assertThat(result.extractedText).contains("HELLO WARRANT")
    assertThat(result.extractedTextSha256).isNotBlank()
  }
}
