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

class ContentHashRenormaliseApplyBackfillTest {

  private val repository: CourtDocumentRepository = mock()
  private val documentManagementApi: HmppsDocumentManagementApi = mock()
  private val pdfTextExtractor: PdfTextExtractor = mock()
  private val normaliser: ExtractedTextNormaliser = mock()

  private val backfill = ContentHashRenormaliseApplyBackfill(repository, documentManagementApi, pdfTextExtractor, normaliser)

  @Test
  fun `selectBatch delegates to findHashedAfter and advances the cursor`() {
    val item = sampleWarrant(extractedTextSha = "existing-hash")
    whenever(repository.findHashedAfter(any(), any())).thenReturn(listOf(item))

    val batch = backfill.selectBatch(cursor = "", batchSize = 200)

    assertThat(batch.items).containsExactly(item)
    assertThat(batch.nextCursor).isEqualTo(item.id.toString())
  }

  @Test
  fun `skips items with no existing content hash without downloading anything`() {
    val item = sampleWarrant(extractedTextSha = null)

    backfill.process(item)

    verifyNoInteractions(documentManagementApi)
  }

  @Test
  fun `writes the new hash and pushes it when the recomputed hash differs`() {
    val item = sampleWarrant(extractedTextSha = "old-hash")
    val bytes = "pdf-bytes".toByteArray()
    whenever(documentManagementApi.downloadFile(item.prisonDocumentId)).thenReturn(bytes)
    whenever(pdfTextExtractor.extractText(bytes)).thenReturn("extracted text")
    whenever(normaliser.normalisedHash("extracted text")).thenReturn("new-hash")

    backfill.process(item)

    assertThat(item.extractedTextSha256).isEqualTo("new-hash")
    verify(repository).save(item)
    verify(documentManagementApi).setFileContentHash(item.prisonDocumentId, "new-hash")
  }

  @Test
  fun `does not write or push when the recomputed hash matches what is already stored`() {
    val item = sampleWarrant(extractedTextSha = "same-hash")
    val bytes = "pdf-bytes".toByteArray()
    whenever(documentManagementApi.downloadFile(item.prisonDocumentId)).thenReturn(bytes)
    whenever(pdfTextExtractor.extractText(bytes)).thenReturn("extracted text")
    whenever(normaliser.normalisedHash("extracted text")).thenReturn("same-hash")

    backfill.process(item)

    assertThat(item.extractedTextSha256).isEqualTo("same-hash")
    verify(repository, never()).save(any())
    verify(documentManagementApi, never()).setFileContentHash(any(), any())
  }

  @Test
  fun `skips items where text can no longer be extracted, leaving the stored hash untouched`() {
    val item = sampleWarrant(extractedTextSha = "old-hash")
    val bytes = "corrupted".toByteArray()
    whenever(documentManagementApi.downloadFile(item.prisonDocumentId)).thenReturn(bytes)
    whenever(pdfTextExtractor.extractText(bytes)).thenReturn(null)

    backfill.process(item)

    assertThat(item.extractedTextSha256).isEqualTo("old-hash")
    verify(repository, never()).save(any())
    verify(documentManagementApi, never()).setFileContentHash(any(), any())
  }

  @Test
  fun `real PCR fixture, previously hashed without normalisation, is corrected and pushed`() {
    val realExtractor = PdfTextExtractor()
    val realNormaliser = ExtractedTextNormaliser(
      ContentNormalisationProperties(patterns = listOf("Register generated on: \\d{2}/\\d{2}/\\d{4}")),
    )
    val bytes = readFixtureBytes("example-register.pdf")
    val text = checkNotNull(realExtractor.extractText(bytes))
    val staleUnnormalisedHash = Sha256.hex(text.toByteArray())
    val expectedNewHash = realNormaliser.normalisedHash(text)
    assertThat(staleUnnormalisedHash).isNotEqualTo(expectedNewHash)

    val item = sampleWarrant(extractedTextSha = staleUnnormalisedHash)
    whenever(documentManagementApi.downloadFile(item.prisonDocumentId)).thenReturn(bytes)
    val realBackfill = ContentHashRenormaliseApplyBackfill(repository, documentManagementApi, realExtractor, realNormaliser)

    realBackfill.process(item)

    assertThat(item.extractedTextSha256).isEqualTo(expectedNewHash)
    verify(repository).save(item)
    verify(documentManagementApi).setFileContentHash(item.prisonDocumentId, expectedNewHash)
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
