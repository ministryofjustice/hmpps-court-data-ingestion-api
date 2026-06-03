package uk.gov.justice.digital.hmpps.courtdataingestionapi.service.ingestion

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtDocumentRepository
import java.util.UUID

class DuplicateResolutionServiceTest {

  private val repository = mock<CourtDocumentRepository>()
  private val service = DuplicateResolutionService(repository)

  @Test
  fun `returns null and does not touch the repository when both hashes are absent`() {
    val result = service.resolve(
      currentDocumentId = UUID.randomUUID(),
      downloadedFileSha256 = null,
      extractedTextSha256 = null,
    )

    Assertions.assertThat(result).isNull()
    verifyNoInteractions(repository)
  }

  @Test
  fun `matches on extracted text hash and excludes the current document`() {
    val current = UUID.randomUUID()
    val duplicateId = UUID.randomUUID()
    val self = mock<CourtDocumentEntity> { on { prisonDocumentId } doReturn current }
    val other = mock<CourtDocumentEntity> { on { prisonDocumentId } doReturn duplicateId }
    whenever(repository.findByExtractedTextSha256("text-hash")).thenReturn(listOf(self, other))

    val result = service.resolve(
      currentDocumentId = current,
      downloadedFileSha256 = "file-hash",
      extractedTextSha256 = "text-hash",
    )

    Assertions.assertThat(result?.duplicateOf).isEqualTo(duplicateId)
    Assertions.assertThat(result?.reason).isEqualTo("matched_on_extracted_text_sha256")
    verify(repository, never()).findByDownloadedFileSha256(any())
  }

  @Test
  fun `falls back to the file hash when the text hash has no other document`() {
    val current = UUID.randomUUID()
    val duplicateId = UUID.randomUUID()
    val other = mock<CourtDocumentEntity> { on { prisonDocumentId } doReturn duplicateId }
    whenever(repository.findByExtractedTextSha256("text-hash")).thenReturn(emptyList())
    whenever(repository.findByDownloadedFileSha256("file-hash")).thenReturn(listOf(other))

    val result = service.resolve(
      currentDocumentId = current,
      downloadedFileSha256 = "file-hash",
      extractedTextSha256 = "text-hash",
    )

    Assertions.assertThat(result?.duplicateOf).isEqualTo(duplicateId)
    Assertions.assertThat(result?.reason).isEqualTo("matched_on_downloaded_file_sha256")
  }

  @Test
  fun `returns null when the only candidate is the current document`() {
    val current = UUID.randomUUID()
    val self = mock<CourtDocumentEntity> { on { prisonDocumentId } doReturn current }
    whenever(repository.findByExtractedTextSha256("text-hash")).thenReturn(listOf(self))
    whenever(repository.findByDownloadedFileSha256("file-hash")).thenReturn(listOf(self))

    val result = service.resolve(
      currentDocumentId = current,
      downloadedFileSha256 = "file-hash",
      extractedTextSha256 = "text-hash",
    )

    Assertions.assertThat(result).isNull()
  }

  @Test
  fun `returns null when no candidate matches either hash`() {
    val current = UUID.randomUUID()
    whenever(repository.findByExtractedTextSha256("text-hash")).thenReturn(emptyList())
    whenever(repository.findByDownloadedFileSha256("file-hash")).thenReturn(emptyList())

    val result = service.resolve(
      currentDocumentId = current,
      downloadedFileSha256 = "file-hash",
      extractedTextSha256 = "text-hash",
    )

    Assertions.assertThat(result).isNull()
  }
}
