package uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.HmppsDocumentManagementApi
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction.ContentNormalisationProperties
import uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction.ExtractedTextNormaliser
import uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction.PdfTextExtractor
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.DestinationType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtDocumentType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.HmctsEventType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtDocumentRepository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.util.Sha256
import java.time.LocalDateTime
import java.util.UUID

class ContentHashRenormaliseDryRunBackfillTest {

  private val repository: CourtDocumentRepository = mock()
  private val recomputer: ContentHashRecomputer = mock()
  private val documentManagementApi: HmppsDocumentManagementApi = mock()

  private val backfill = ContentHashRenormaliseDryRunBackfill(repository, recomputer)

  @Test
  fun `selectBatch delegates to findHashedAfter and advances the cursor`() {
    val item = sampleWarrant(extractedTextSha = "existing-hash")
    whenever(repository.findHashedAfter(any(), any())).thenReturn(listOf(item))

    val batch = backfill.selectBatch(cursor = "", batchSize = 200)

    assertThat(batch.items).containsExactly(item)
    assertThat(batch.nextCursor).isEqualTo(item.id.toString())
  }

  @Test
  fun `skips items the recomputer declines to score, without writing anything`() {
    val item = sampleWarrant(extractedTextSha = null)
    whenever(recomputer.recompute(item)).thenReturn(null)

    backfill.process(item)

    verify(repository, never()).save(any())
  }

  @Test
  fun `never writes to the repository or pushes to the document store, even when the hash would change`() {
    val item = sampleWarrant(extractedTextSha = "old-hash")
    whenever(recomputer.recompute(item)).thenReturn(ContentHashRecomputation("old-hash", "new-hash"))

    backfill.process(item)

    assertThat(item.extractedTextSha256).isEqualTo("old-hash") // unchanged in memory
    verify(repository, never()).save(any())
    verifyNoInteractions(documentManagementApi)
  }

  @Test
  fun `does nothing further when the recomputed hash matches what is already stored`() {
    val item = sampleWarrant(extractedTextSha = "same-hash")
    whenever(recomputer.recompute(item)).thenReturn(ContentHashRecomputation("same-hash", "same-hash"))

    backfill.process(item)

    verify(repository, never()).save(any())
  }

  @Test
  fun `real PCR fixture, hashed without normalisation first, is flagged as changing once normalised`() {
    val realExtractor = PdfTextExtractor()
    val realNormaliser = ExtractedTextNormaliser(
      ContentNormalisationProperties(patterns = listOf("Register generated on: \\d{2}/\\d{2}/\\d{4}")),
    )
    val bytes = readFixtureBytes("example-register.pdf")
    val text = checkNotNull(realExtractor.extractText(bytes))
    val staleUnnormalisedHash = Sha256.hex(text.toByteArray())
    val expectedNewHash = realNormaliser.getNormalisedHash(text)
    assertThat(staleUnnormalisedHash).isNotEqualTo(expectedNewHash)

    val realDocumentManagementApi: HmppsDocumentManagementApi = mock()
    val item = sampleWarrant(extractedTextSha = staleUnnormalisedHash)
    whenever(realDocumentManagementApi.downloadFile(item.prisonDocumentId)).thenReturn(bytes)
    val realRecomputer = ContentHashRecomputer(realDocumentManagementApi, realExtractor, realNormaliser)
    val realBackfill = ContentHashRenormaliseDryRunBackfill(repository, realRecomputer)

    realBackfill.process(item)

    // dry run: still nothing written, even though a real mismatch was detected
    assertThat(item.extractedTextSha256).isEqualTo(staleUnnormalisedHash)
    verify(repository, never()).save(any())
  }

  private fun sampleWarrant(extractedTextSha: String?): CourtDocumentEntity = CourtDocumentEntity(
    defendantId = UUID.randomUUID(),
    hmctsCourtDocumentId = UUID.randomUUID(),
    prisonDocumentId = UUID.randomUUID(),
    hmctsCourtHearingId = UUID.fromString("509b295e-22d1-4cc0-9925-d5690503ce3c"),
    prisonEmailAddress = "OMU.HolmeHouse@justice.gov.uk",
    eventType = HmctsEventType.WEE_SendingToCrownCourtForTrial,
    courtDocumentType = CourtDocumentType.REMAND_WARRANT,
    documentGeneratedTimestamp = LocalDateTime.now(),
    addressedPrison = "HHI",
    downloadedFileSha256 = "some-file-hash",
    extractedTextSha256 = extractedTextSha,
    deliverySource = DestinationType.PRISON,
  )

  private fun readFixtureBytes(name: String): ByteArray {
    val stream = checkNotNull(javaClass.getResourceAsStream("/test-fixtures/$name")) {
      "Test fixture PDF not found on classpath at /test-fixtures/$name"
    }
    return stream.use { it.readBytes() }
  }
}
