package uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtDocumentType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.HmctsEventType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtDocumentRepository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.FileService
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID

class MirrorBackfillTest {

  private val repository: CourtDocumentRepository = mock()
  private val fileService: FileService = mock()
  private val backfill = MirrorBackfill(repository, fileService, METADATA_VERSION)

  @Test
  fun `selectBatch passes ZERO_UUID on empty cursor`() {
    val first = sampleWarrant(extractedTextSha = "604576bd")
    whenever(repository.findUnmirroredAfter(any(), any(), any())).thenReturn(listOf(first))

    val batch = backfill.selectBatch(cursor = "", batchSize = 100)

    assertThat(first.metadataVersion).isLessThan(METADATA_VERSION)
    assertThat(batch.items).containsExactly(first.id)
    assertThat(batch.nextCursor).isEqualTo(first.id.toString())
  }

  @Test
  fun `selectBatch returns the input cursor unchanged when no items remain`() {
    whenever(repository.findUnmirroredAfter(any(), any(), any())).thenReturn(emptyList())

    val batch = backfill.selectBatch(cursor = UUID.randomUUID().toString(), batchSize = 100)

    assertThat(batch.items).isEmpty()
  }

  @Test
  fun `process marks the metadata version for the organisation it mirrored on full success`() {
    val item = sampleWarrant(extractedTextSha = "604576bd")
    whenever(fileService.mirrorEnrichmentToDocumentStore(item))
      .thenReturn(FileService.MirrorOutcome(contentHashPushed = true, metadataPushed = true))
    setupCourtDocumentRepositoryMock(item)

    backfill.process(item.id)

    verify(repository).markMirrored(eq(item.id), eq(METADATA_VERSION), any(), eq("PRISON"))
  }

  @Test
  fun `process does not save the whole row, so a classification applied meanwhile is not written back`() {
    val item = sampleWarrant(extractedTextSha = "604576bd")
    val initialMetadataVersion = item.metadataVersion
    whenever(fileService.mirrorEnrichmentToDocumentStore(item))
      .thenReturn(FileService.MirrorOutcome(contentHashPushed = true, metadataPushed = true))
    setupCourtDocumentRepositoryMock(item)

    backfill.process(item.id)

    verify(repository, never()).save(any<CourtDocumentEntity>())
    assertThat(item.metadataVersion).isEqualTo(initialMetadataVersion)
  }

  @Test
  fun `process throws and does not update metadata version or mark on content-hash failure so the row stays in scope`() {
    val item = sampleWarrant(extractedTextSha = "604576bd")
    val initialMetadataVersion = item.metadataVersion
    val failure = RuntimeException("doc store 503")
    whenever(fileService.mirrorEnrichmentToDocumentStore(item)).thenReturn(
      FileService.MirrorOutcome(
        contentHashPushed = false,
        metadataPushed = true,
        contentHashError = failure,
      ),
    )
    setupCourtDocumentRepositoryMock(item)

    assertThatThrownBy { backfill.process(item.id) }.isEqualTo(failure)
    assertThat(item.metadataVersion).isLessThan(METADATA_VERSION)
    assertThat(item.metadataVersion).isEqualTo(initialMetadataVersion)
    assertThat(item.metadataUpdatedAt).isNull()
    verify(repository, never()).markMirrored(any(), any(), any(), anyOrNull())
  }

  @Test
  fun `process throws and does not update metadata version or mark on metadata failure even if content hash succeeded`() {
    val item = sampleWarrant(extractedTextSha = "604576bd").apply {
      addressedOrganisation = "PECS"
    }
    val initialMetadataVersion = item.metadataVersion
    val failure = RuntimeException("merge 504")
    whenever(fileService.mirrorEnrichmentToDocumentStore(item)).thenReturn(
      FileService.MirrorOutcome(
        contentHashPushed = true,
        metadataPushed = false,
        metadataError = failure,
      ),
    )
    setupCourtDocumentRepositoryMock(item)

    assertThatThrownBy { backfill.process(item.id) }.isEqualTo(failure)
    assertThat(item.metadataVersion).isLessThan(METADATA_VERSION)
    assertThat(item.metadataVersion).isEqualTo(initialMetadataVersion)
    assertThat(item.metadataUpdatedAt).isNull()
    verify(repository, never()).markMirrored(any(), any(), any(), anyOrNull())
  }

  @Test
  fun `process handles rows with no extracted text by treating content hash as already pushed`() {
    val item = sampleWarrant(extractedTextSha = null)
    whenever(fileService.mirrorEnrichmentToDocumentStore(item))
      .thenReturn(FileService.MirrorOutcome(contentHashPushed = true, metadataPushed = true))
    setupCourtDocumentRepositoryMock(item)

    backfill.process(item.id)

    verify(repository).markMirrored(eq(item.id), eq(METADATA_VERSION), any(), eq("PRISON"))
  }

  private fun sampleWarrant(extractedTextSha: String?): CourtDocumentEntity = CourtDocumentEntity(
    masterDefendantId = UUID.randomUUID(),
    hmctsCourtDocumentId = UUID.randomUUID(),
    prisonDocumentId = UUID.randomUUID(),
    hmctsCourtHearingId = UUID.fromString("509b295e-22d1-4cc0-9925-d5690503ce3c"),
    prisonEmailAddress = "OMU.HolmeHouse@justice.gov.uk",
    eventType = HmctsEventType.WEE_SendingToCrownCourtForTrial,
    courtDocumentType = CourtDocumentType.REMAND_WARRANT,
    documentGeneratedTimestamp = LocalDateTime.now(),
    addressedPrison = "HHI",
    downloadedFileSha256 = "1e8c08ae751bcfb0fd81b3f3abb32659a98a2171c30bc5c8e153791bc7060040",
    extractedTextSha256 = extractedTextSha,
    addressedOrganisation = "PRISON",
  )

  private fun setupCourtDocumentRepositoryMock(mockedDocument: CourtDocumentEntity) {
    whenever(repository.findById(any())).thenReturn(Optional.of(mockedDocument))
  }

  companion object {
    const val METADATA_VERSION: Int = 1
  }
}
