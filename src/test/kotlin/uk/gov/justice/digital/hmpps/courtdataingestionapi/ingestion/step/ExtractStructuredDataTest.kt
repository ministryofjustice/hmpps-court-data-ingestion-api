package uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.step

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.IngestionContext
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.extraction.ExtractionService
import java.util.UUID

class ExtractStructuredDataTest {

  private val extractionService = mock<ExtractionService>()
  private val enricher = ExtractStructuredData(extractionService)

  @Test
  fun `calls structured extraction when extracted text exists`() {
    val documentId = UUID.randomUUID()
    whenever(
      extractionService.extractStructuredDataAndStore(documentId, "text", "file-hash"),
    ).thenReturn(mock())

    val input = IngestionContext(
      prisonEmailAddress = "omu.example@example.com",
      prisonDocumentId = documentId,
      extractedText = "text",
      downloadedFileSha256 = "file-hash",
      extractedTextSha256 = "text-hash",
    )

    val result = enricher.enrich(input)

    verify(extractionService).extractStructuredDataAndStore(documentId, "text", "file-hash")
    assertThat(result).isEqualTo(input)
  }

  @Test
  fun `skips structured extraction when there is no document id`() {
    enricher.enrich(IngestionContext(prisonEmailAddress = "omu.example@example.com", prisonDocumentId = null, extractedText = "text"))

    verify(extractionService, never()).extractStructuredDataAndStore(any(), any(), any())
  }

  @Test
  fun `skips structured extraction when extracted text is blank`() {
    val input = IngestionContext(prisonEmailAddress = "omu.example@example.com", prisonDocumentId = UUID.randomUUID(), extractedText = "   ")

    val result = enricher.enrich(input)

    verify(extractionService, never()).extractStructuredDataAndStore(any(), any(), any())
    assertThat(result.warnings).contains("Structured extraction skipped: extracted text is empty")
  }

  @Test
  fun `swallows extraction failure so ingestion is not broken`() {
    val documentId = UUID.randomUUID()
    whenever(extractionService.extractStructuredDataAndStore(documentId, "text", null))
      .thenThrow(RuntimeException("extraction blew up"))

    val input = IngestionContext(
      prisonEmailAddress = "omu.example@example.com",
      prisonDocumentId = documentId,
      extractedText = "text",
    )

    val result = enricher.enrich(input)

    assertThat(result).isEqualTo(input)
  }
}
