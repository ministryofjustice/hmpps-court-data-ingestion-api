package uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.extraction.ExtractionService
import java.util.UUID

class ExtractionEnricherTest {

  private val extractionService = mock<ExtractionService>()
  private val enricher = ExtractionEnricher(extractionService)

  @Test
  fun `triggers extraction for the document and returns the context unchanged`() {
    val documentId = UUID.randomUUID()
    whenever(extractionService.extractAndStore(documentId)).thenReturn(mock())
    val input = context(documentId)

    val result = enricher.enrich(input)

    verify(extractionService).extractAndStore(documentId)
    assertThat(result).isEqualTo(input)
  }

  @Test
  fun `skips extraction when there is no document id`() {
    enricher.enrich(context(documentId = null))

    verify(extractionService, never()).extractAndStore(any())
  }

  @Test
  fun `swallows extraction failure so ingestion is not broken`() {
    val documentId = UUID.randomUUID()
    whenever(extractionService.extractAndStore(documentId)).thenThrow(RuntimeException("extraction blew up"))
    val input = context(documentId)

    val result = enricher.enrich(input)

    assertThat(result).isEqualTo(input)
  }

  private fun context(documentId: UUID?) = IngestionContext(
    prisonEmailAddress = "omu.example@example.com",
    prisonDocumentId = documentId,
  )
}
