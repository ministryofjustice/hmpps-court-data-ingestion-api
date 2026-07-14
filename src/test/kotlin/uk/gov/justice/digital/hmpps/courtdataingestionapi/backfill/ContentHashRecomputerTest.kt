package uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.HmppsDocumentManagementApi
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction.ExtractedTextNormaliser
import uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction.PdfTextExtractor
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.DestinationType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtDocumentType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.HmctsEventType
import java.time.LocalDateTime
import java.util.UUID

class ContentHashRecomputerTest {

  private val documentManagementApi: HmppsDocumentManagementApi = mock()
  private val pdfTextExtractor: PdfTextExtractor = mock()
  private val normaliser: ExtractedTextNormaliser = mock()

  private val recomputer = ContentHashRecomputer(documentManagementApi, pdfTextExtractor, normaliser)

  @Test
  fun `returns null when the item has no existing content hash to compare against`() {
    val item = sampleWarrant(extractedTextSha = null)

    val result = recomputer.recompute(item)

    assertThat(result).isNull()
  }

  @Test
  fun `returns null when the item has a blank content hash`() {
    val item = sampleWarrant(extractedTextSha = "")

    val result = recomputer.recompute(item)

    assertThat(result).isNull()
  }

  @Test
  fun `returns null when text can no longer be extracted from the downloaded bytes`() {
    val item = sampleWarrant(extractedTextSha = "old-hash")
    val bytes = "corrupted".toByteArray()
    whenever(documentManagementApi.downloadFile(item.prisonDocumentId)).thenReturn(bytes)
    whenever(pdfTextExtractor.extractText(bytes)).thenReturn(null)

    val result = recomputer.recompute(item)

    assertThat(result).isNull()
  }

  @Test
  fun `returns the current and recomputed hash, flagged as changed when they differ`() {
    val item = sampleWarrant(extractedTextSha = "old-hash")
    val bytes = "pdf-bytes".toByteArray()
    whenever(documentManagementApi.downloadFile(item.prisonDocumentId)).thenReturn(bytes)
    whenever(pdfTextExtractor.extractText(bytes)).thenReturn("extracted text")
    whenever(normaliser.getNormalisedHash("extracted text")).thenReturn("new-hash")

    val result = recomputer.recompute(item)

    assertThat(result).isEqualTo(ContentHashRecomputation("old-hash", "new-hash"))
    assertThat(result!!.changed).isTrue()
  }

  @Test
  fun `flags unchanged when the recomputed hash matches the current one`() {
    val item = sampleWarrant(extractedTextSha = "same-hash")
    val bytes = "pdf-bytes".toByteArray()
    whenever(documentManagementApi.downloadFile(item.prisonDocumentId)).thenReturn(bytes)
    whenever(pdfTextExtractor.extractText(bytes)).thenReturn("extracted text")
    whenever(normaliser.getNormalisedHash("extracted text")).thenReturn("same-hash")

    val result = recomputer.recompute(item)

    assertThat(result!!.changed).isFalse()
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
    downloadedFileSha256 = "some-file-hash",
    extractedTextSha256 = extractedTextSha,
    deliverySource = DestinationType.PRISON,
  )
}
