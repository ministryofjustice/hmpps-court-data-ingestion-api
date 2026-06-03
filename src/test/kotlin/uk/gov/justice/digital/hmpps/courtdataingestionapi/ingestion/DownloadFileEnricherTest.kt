package uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.HmppsDocumentManagementApi
import java.util.UUID

class DownloadFileEnricherTest {

  private val documentApi = mock<HmppsDocumentManagementApi>()
  private val enricher = DownloadFileEnricher(documentApi)

  @Test
  fun `downloads file and stores bytes in context`() {
    val documentId = UUID.randomUUID()
    val bytes = "hello".toByteArray()
    whenever(documentApi.downloadFile(documentId)).thenReturn(bytes)

    val input = context(documentId)

    val result = enricher.enrich(input)

    verify(documentApi).downloadFile(documentId)
    assertThat(result.downloadedFileBytes).isEqualTo(bytes)
  }

  @Test
  fun `skips download when there is no document id`() {
    val input = context(null)

    val result = enricher.enrich(input)

    verify(documentApi, never()).downloadFile(any())
    assertThat(result).isEqualTo(input)
  }

  @Test
  fun `adds a warning and continues when the download fails`() {
    val documentId = UUID.randomUUID()
    whenever(documentApi.downloadFile(documentId)).thenThrow(RuntimeException("boom"))

    val result = enricher.enrich(context(documentId))

    assertThat(result.downloadedFileBytes).isNull()
    assertThat(result.warnings).contains("File download failed")
  }

  private fun context(documentId: UUID?) = IngestionContext(
    prisonEmailAddress = "omu.example@example.com",
    prisonDocumentId = documentId,
  )
}
