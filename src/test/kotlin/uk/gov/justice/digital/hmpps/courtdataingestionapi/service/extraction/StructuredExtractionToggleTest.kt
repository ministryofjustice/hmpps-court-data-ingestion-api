package uk.gov.justice.digital.hmpps.courtdataingestionapi.service.extraction

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.HmppsDocumentManagementApi
import uk.gov.justice.digital.hmpps.courtdataingestionapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.ExtractionResultEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction.format.FormatModel
import uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction.format.FormatModelRegistry
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.ExtractionResultRepository
import java.util.UUID

class StructuredExtractionToggleTest {

  private val documentApi = mock<HmppsDocumentManagementApi>()
  private val repository = mock<ExtractionResultRepository>()
  private val formatModels = mock<FormatModelRegistry>()
  private val pipeline = mock<ExtractionPipeline>()

  private fun service(enabled: Boolean) = ExtractionService(
    documentApi = documentApi,
    repository = repository,
    formatModels = formatModels,
    pipeline = pipeline,
    objectMapper = ObjectMapper(),
    featureToggles = FeatureToggles(structuredExtraction = enabled),
    extractorVersion = "test",
  )

  private val documentId = UUID.randomUUID()

  @Test
  fun `disabled - the ingestion entry point does nothing and returns null`() {
    val result = service(enabled = false)
      .extractStructuredDataAndStore(documentId, "some extracted text", "file-sha")

    assertThat(result).isNull()
    verifyNoInteractions(documentApi, repository, formatModels, pipeline)
  }

  @Test
  fun `disabled - the backfill entry point does nothing and returns null`() {
    val result = service(enabled = false).extractAndStore(documentId)

    assertThat(result).isNull()
    verifyNoInteractions(documentApi, repository, formatModels, pipeline)
  }

  @Test
  fun `enabled - the ingestion entry point proceeds past the guard`() {
    val model = mock<FormatModel> {
      on { id } doReturn "fmt"
      on { version } doReturn 1
    }
    whenever(formatModels.active()).thenReturn(model)
    val existing = mock<ExtractionResultEntity> { on { status } doReturn "OK" }
    whenever(
      repository.findByDocumentIdAndFormatIdAndFormatVersionAndExtractorVersion(any(), any(), any(), any()),
    ).thenReturn(existing)

    val result = service(enabled = true)
      .extractStructuredDataAndStore(documentId, "some extracted text", "file-sha")

    assertThat(result).isSameAs(existing)
    verify(formatModels).active()
    verify(pipeline, never()).extractFromText(any(), any(), any())
  }
}
