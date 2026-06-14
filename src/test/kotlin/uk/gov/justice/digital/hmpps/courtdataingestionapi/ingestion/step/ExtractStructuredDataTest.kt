package uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.step

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
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
  fun `runs structured extraction on the downloaded pdf bytes`() {
    val documentId = UUID.randomUUID()
    whenever(
      extractionService.extractStructuredDataAndStore(eq(documentId), any(), eq("file-hash")),
    ).thenReturn(mock())

    val input = IngestionContext(
      prisonEmailAddress = "omu.example@example.com",
      prisonDocumentId = documentId,
      downloadedFileBytes = byteArrayOf('%'.code.toByte(), 'P'.code.toByte(), 'D'.code.toByte(), 'F'.code.toByte()),
      downloadedFileSha256 = "file-hash",
      hearingId = null,
      caseReferences = null,
    )

    val result = enricher.enrich(input)

    verify(extractionService).extractStructuredDataAndStore(eq(documentId), any(), eq("file-hash"))
    assertThat(result).isEqualTo(input)
  }

  @Test
  fun `skips structured extraction when there is no document id`() {
    enricher.enrich(
      IngestionContext(
        prisonEmailAddress = "omu.example@example.com",
        prisonDocumentId = null,
        downloadedFileBytes = byteArrayOf(1, 2, 3),
        hearingId = null,
        caseReferences = null,
      ),
    )

    verify(extractionService, never()).extractStructuredDataAndStore(any(), any(), any())
  }

  @Test
  fun `skips structured extraction when there is no downloaded file`() {
    val input = IngestionContext(
      prisonEmailAddress = "omu.example@example.com",
      prisonDocumentId = UUID.randomUUID(),
      downloadedFileBytes = null,
      hearingId = null,
      caseReferences = null,
    )

    val result = enricher.enrich(input)

    verify(extractionService, never()).extractStructuredDataAndStore(any(), any(), any())
    assertThat(result.warnings).contains("Structured extraction skipped: no downloaded file")
  }

  @Test
  fun `swallows extraction failure so ingestion is not broken`() {
    val documentId = UUID.randomUUID()
    whenever(extractionService.extractStructuredDataAndStore(eq(documentId), any(), any()))
      .thenThrow(RuntimeException("extraction blew up"))

    val input = IngestionContext(
      prisonEmailAddress = "omu.example@example.com",
      prisonDocumentId = documentId,
      downloadedFileBytes = byteArrayOf(1, 2, 3),
      hearingId = null,
      caseReferences = null,
    )

    val result = enricher.enrich(input)

    assertThat(result).isEqualTo(input)
  }
}
