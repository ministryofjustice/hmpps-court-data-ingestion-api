package uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.DestinationType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtDocumentType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.HmctsEventType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtDocumentRepository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.FileService
import java.time.LocalDateTime
import java.util.UUID

class MirrorBackfillTest {

  private val repository: CourtDocumentRepository = mock()
  private val fileService: FileService = mock()
  private val backfill = MirrorBackfill(repository, fileService)

  @Test
  fun `selectBatch passes ZERO_UUID on empty cursor`() {
    val first = sampleWarrant(extractedTextSha = "604576bd")
    whenever(repository.findUnmirroredAfter(any(), any())).thenReturn(listOf(first))

    val batch = backfill.selectBatch(cursor = "", batchSize = 100)

    assertThat(batch.items).containsExactly(first)
    assertThat(batch.nextCursor).isEqualTo(first.id.toString())
  }

  @Test
  fun `selectBatch returns the input cursor unchanged when no items remain`() {
    whenever(repository.findUnmirroredAfter(any(), any())).thenReturn(emptyList())

    val batch = backfill.selectBatch(cursor = UUID.randomUUID().toString(), batchSize = 100)

    assertThat(batch.items).isEmpty()
  }

  @Test
  fun `process sets mirroredToDocStoreAt on full success`() {
    val item = sampleWarrant(extractedTextSha = "604576bd")
    whenever(fileService.mirrorEnrichmentToDocumentStore(item))
      .thenReturn(FileService.MirrorOutcome(contentHashPushed = true, metadataPushed = true))

    backfill.process(item)

    assertThat(item.mirroredToDocStoreAt).isNotNull
    verify(repository).save(item)
  }

  @Test
  fun `process throws and does not mark on content-hash failure so the row stays in scope`() {
    val item = sampleWarrant(extractedTextSha = "604576bd")
    val failure = RuntimeException("doc store 503")
    whenever(fileService.mirrorEnrichmentToDocumentStore(item)).thenReturn(
      FileService.MirrorOutcome(
        contentHashPushed = false,
        metadataPushed = true,
        contentHashError = failure,
      ),
    )

    assertThatThrownBy { backfill.process(item) }.isEqualTo(failure)
    assertThat(item.mirroredToDocStoreAt).isNull()
  }

  @Test
  fun `process throws and does not mark on metadata failure even if content hash succeeded`() {
    // Important: content hash is the dedup-critical call. If it succeeded but metadata failed we
    // still leave the row unmarked, so a retry will idempotently re-push the content hash (no-op
    // at doc store) and have another go at metadata. Marking the row done would strand metadata.
    val item = sampleWarrant(extractedTextSha = "604576bd").apply {
      deliverySource = DestinationType.PECS
    }
    val failure = RuntimeException("merge 504")
    whenever(fileService.mirrorEnrichmentToDocumentStore(item)).thenReturn(
      FileService.MirrorOutcome(
        contentHashPushed = true,
        metadataPushed = false,
        metadataError = failure,
      ),
    )

    assertThatThrownBy { backfill.process(item) }.isEqualTo(failure)
    assertThat(item.mirroredToDocStoreAt).isNull()
  }

  @Test
  fun `process handles rows with no extracted text by treating content hash as already pushed`() {
    // A row with no extracted_text_sha256 has nothing to push for the content hash, so FileService
    // returns contentHashPushed=true vacuously. The mirror should still mark the row done.
    val item = sampleWarrant(extractedTextSha = null)
    whenever(fileService.mirrorEnrichmentToDocumentStore(item))
      .thenReturn(FileService.MirrorOutcome(contentHashPushed = true, metadataPushed = true))

    backfill.process(item)

    assertThat(item.mirroredToDocStoreAt).isNotNull
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
    deliverySource = DestinationType.PRISON,
  )
}
