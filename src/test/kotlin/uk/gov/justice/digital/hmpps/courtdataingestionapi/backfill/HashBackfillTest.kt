package uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
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
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.FileService
import uk.gov.justice.digital.hmpps.courtdataingestionapi.util.Sha256
import java.time.LocalDateTime
import java.util.UUID

class HashBackfillTest {

  private val repository: CourtDocumentRepository = mock()
  private val documentManagementApi: HmppsDocumentManagementApi = mock()
  private val fileService: FileService = mock()
  private val pdfTextExtractor: PdfTextExtractor = mock()
  private val normaliser: ExtractedTextNormaliser = mock()

  private val backfill = HashBackfill(repository, documentManagementApi, fileService, pdfTextExtractor, normaliser, METADATA_VERSION)

  @Test
  fun `selectBatch delegates to findUnhashedAfter and advances the cursor`() {
    val first = sampleWarrant(downloadedFileSha = null, extractedTextSha = null)
    whenever(repository.findUnhashedAfter(any(), any())).thenReturn(listOf(first))

    val batch = backfill.selectBatch(cursor = "", batchSize = 100)

    assertThat(batch.items).containsExactly(first)
    assertThat(batch.nextCursor).isEqualTo(first.id.toString())
  }

  @Test
  fun `process downloads once and sets both hashes when both are blank`() {
    val item = sampleWarrant(downloadedFileSha = null, extractedTextSha = null)
    val bytes = "pdf-bytes".toByteArray()
    whenever(documentManagementApi.downloadFile(item.prisonDocumentId)).thenReturn(bytes)
    whenever(pdfTextExtractor.extractText(bytes)).thenReturn("extracted text")
    whenever(normaliser.getNormalisedHash("extracted text")).thenReturn("normalised-hash")
    whenever(fileService.mirrorEnrichmentToDocumentStore(item))
      .thenReturn(FileService.MirrorOutcome(contentHashPushed = true, metadataPushed = true))

    backfill.process(item)

    assertThat(item.downloadedFileSha256).isEqualTo(Sha256.hex(bytes))
    assertThat(item.extractedTextSha256).isEqualTo("normalised-hash")
    assertThat(item.metadataVersion).isEqualTo(METADATA_VERSION)
    verify(documentManagementApi, org.mockito.kotlin.times(1)).downloadFile(item.prisonDocumentId)
    verify(repository, org.mockito.kotlin.times(2)).save(item)
  }

  @Test
  fun `process does not download when both hashes are already present`() {
    val item = sampleWarrant(downloadedFileSha = "already-set", extractedTextSha = "already-set-too")
    val initialMetadataVersion = item.metadataVersion
    whenever(fileService.mirrorEnrichmentToDocumentStore(item))
      .thenReturn(FileService.MirrorOutcome(contentHashPushed = true, metadataPushed = true))

    backfill.process(item)

    verify(documentManagementApi, never()).downloadFile(any())
    assertThat(item.downloadedFileSha256).isEqualTo("already-set")
    assertThat(item.extractedTextSha256).isEqualTo("already-set-too")
    assertThat(initialMetadataVersion).isLessThan(METADATA_VERSION)
    assertThat(item.metadataVersion).isEqualTo(METADATA_VERSION)
  }

  @Test
  fun `process leaves content hash unset when extraction yields no text, but still sets file hash`() {
    val item = sampleWarrant(downloadedFileSha = null, extractedTextSha = null)
    val initialMetadataVersion = item.metadataVersion
    val bytes = "not-really-a-pdf".toByteArray()
    whenever(documentManagementApi.downloadFile(item.prisonDocumentId)).thenReturn(bytes)
    whenever(pdfTextExtractor.extractText(bytes)).thenReturn(null)
    whenever(fileService.mirrorEnrichmentToDocumentStore(item))
      .thenReturn(FileService.MirrorOutcome(contentHashPushed = true, metadataPushed = true))

    backfill.process(item)

    assertThat(item.downloadedFileSha256).isEqualTo(Sha256.hex(bytes))
    assertThat(item.extractedTextSha256).isNull()
    assertThat(initialMetadataVersion).isLessThan(METADATA_VERSION)
    assertThat(item.metadataVersion).isEqualTo(METADATA_VERSION)
    verify(normaliser, never()).getNormalisedHash(any())
  }

  @Test
  fun `process throws and does not update metadata version or as mark mirrored on mirror failure`() {
    val item = sampleWarrant(downloadedFileSha = "set", extractedTextSha = "set")
    val initialMetadataVersion = item.metadataVersion
    val failure = RuntimeException("doc store 503")
    whenever(fileService.mirrorEnrichmentToDocumentStore(item)).thenReturn(
      FileService.MirrorOutcome(contentHashPushed = false, metadataPushed = true, contentHashError = failure),
    )

    assertThatThrownBy { backfill.process(item) }.isEqualTo(failure)
    assertThat(item.metadataVersion).isLessThan(METADATA_VERSION)
    assertThat(item.metadataVersion).isEqualTo(initialMetadataVersion)
    assertThat(item.mirroredToDocStoreAt).isNull()
  }

  @Test
  fun `process updates metadata version and marks mirroredToDocStoreAt on full success`() {
    val item = sampleWarrant(downloadedFileSha = "set", extractedTextSha = "set")
    val initialMetadataVersion = item.metadataVersion
    whenever(fileService.mirrorEnrichmentToDocumentStore(item))
      .thenReturn(FileService.MirrorOutcome(contentHashPushed = true, metadataPushed = true))

    backfill.process(item)

    assertThat(initialMetadataVersion).isLessThan(METADATA_VERSION)
    assertThat(item.metadataVersion).isEqualTo(METADATA_VERSION)
    assertThat(item.mirroredToDocStoreAt).isNotNull
  }

  @Test
  fun `end-to-end against the real PCR fixture produces a normalised content hash`() {
    val realExtractor = PdfTextExtractor()
    val realNormaliser = ExtractedTextNormaliser(
      ContentNormalisationProperties(patterns = listOf("Register generated on: \\d{2}/\\d{2}/\\d{4}")),
    )
    val backfillWithRealComponents = HashBackfill(repository, documentManagementApi, fileService, realExtractor, realNormaliser, METADATA_VERSION)

    val item = sampleWarrant(downloadedFileSha = null, extractedTextSha = null)
    val initialMetadataVersion = item.metadataVersion
    val bytes = readFixtureBytes("example-register.pdf")
    whenever(documentManagementApi.downloadFile(item.prisonDocumentId)).thenReturn(bytes)
    whenever(fileService.mirrorEnrichmentToDocumentStore(item))
      .thenReturn(FileService.MirrorOutcome(contentHashPushed = true, metadataPushed = true))

    backfillWithRealComponents.process(item)

    val expectedText = checkNotNull(realExtractor.extractText(bytes))
    assertThat(item.downloadedFileSha256).isEqualTo(Sha256.hex(bytes))
    assertThat(item.extractedTextSha256).isEqualTo(realNormaliser.getNormalisedHash(expectedText))
    assertThat(initialMetadataVersion).isLessThan(METADATA_VERSION)
    assertThat(item.metadataVersion).isEqualTo(METADATA_VERSION)
  }

  private fun sampleWarrant(downloadedFileSha: String?, extractedTextSha: String?): CourtDocumentEntity = CourtDocumentEntity(
    masterDefendantId = UUID.randomUUID(),
    hmctsCourtDocumentId = UUID.randomUUID(),
    prisonDocumentId = UUID.randomUUID(),
    hmctsCourtHearingId = UUID.fromString("509b295e-22d1-4cc0-9925-d5690503ce3c"),
    prisonEmailAddress = "OMU.HolmeHouse@justice.gov.uk",
    eventType = HmctsEventType.WEE_SendingToCrownCourtForTrial,
    courtDocumentType = CourtDocumentType.REMAND_WARRANT,
    documentGeneratedTimestamp = LocalDateTime.now(),
    addressedPrison = "HHI",
    downloadedFileSha256 = downloadedFileSha,
    extractedTextSha256 = extractedTextSha,
    deliverySource = DestinationType.PRISON,
  )

  private fun readFixtureBytes(name: String): ByteArray {
    val stream = checkNotNull(javaClass.getResourceAsStream("/test-fixtures/$name")) {
      "Test fixture PDF not found on classpath at /test-fixtures/$name"
    }
    return stream.use { it.readBytes() }
  }

  companion object {
    const val METADATA_VERSION: Int = 1
  }
}
