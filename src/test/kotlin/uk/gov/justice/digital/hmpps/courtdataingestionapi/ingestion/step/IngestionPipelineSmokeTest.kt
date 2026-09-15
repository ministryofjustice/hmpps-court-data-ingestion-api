package uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.step

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction.ContentNormalisationProperties
import uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction.ExtractedTextNormaliser
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.IngestionContext
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.IngestionEnrichmentFlow
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.TestPdfFactory

class IngestionPipelineSmokeTest {

  @Test
  fun `threads file hash text extraction and text hash across a simple pipeline`() {
    val flow = IngestionEnrichmentFlow(
      listOf(
        HashDownloadedFile(),
        ExtractPdfText(),
        HashExtractedText(ExtractedTextNormaliser(ContentNormalisationProperties())),
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
