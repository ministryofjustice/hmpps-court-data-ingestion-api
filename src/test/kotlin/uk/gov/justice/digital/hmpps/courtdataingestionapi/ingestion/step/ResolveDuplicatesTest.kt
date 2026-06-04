package uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.ingestion.DuplicateResolutionOutcome
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.ingestion.DuplicateResolutionService
import java.util.UUID

class ResolveDuplicatesTest {

  private val service = mock<DuplicateResolutionService>()
  private val enricher = ResolveDuplicates(service)

  @Test
  fun `sets duplicateOf when service returns match`() {
    val documentId = UUID.randomUUID()
    val duplicateOf = UUID.randomUUID()
    whenever(service.resolve(documentId, "file-hash", "text-hash"))
      .thenReturn(DuplicateResolutionOutcome(duplicateOf, "duplicate found"))

    val input = IngestionContext(
      prisonEmailAddress = null,
      prisonDocumentId = documentId,
      downloadedFileSha256 = "file-hash",
      extractedTextSha256 = "text-hash",
      destinationType = DestinationType.PECS,
    )

    val result = enricher.enrich(input)

    verify(service).resolve(documentId, "file-hash", "text-hash")
    assertThat(result.duplicateOf).isEqualTo(duplicateOf)
    assertThat(result.warnings).contains("duplicate found")
  }

  @Test
  fun `skips duplicate resolution when hashes are absent`() {
    val input = IngestionContext(
      prisonEmailAddress = null,
      prisonDocumentId = UUID.randomUUID(),
      destinationType = DestinationType.PECS,
    )

    val result = enricher.enrich(input)

    assertThat(result.duplicateOf).isNull()
    assertThat(result.warnings).contains("Duplicate resolution skipped because no hashes were available")
  }
}
