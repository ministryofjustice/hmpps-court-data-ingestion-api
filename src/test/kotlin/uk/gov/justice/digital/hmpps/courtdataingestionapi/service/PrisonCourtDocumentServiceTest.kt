package uk.gov.justice.digital.hmpps.courtdataingestionapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtHearingEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtDocumentType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.HmctsEventType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtDocumentRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class PrisonCourtDocumentServiceTest {

  private val courtDocumentRepository: CourtDocumentRepository = mock()
  private val prisonerSearchService: PrisonerSearchService = mock()
  private val service = PrisonCourtDocumentService(courtDocumentRepository, prisonerSearchService)

  private fun documents(count: Int) = (1..count).map {
    CourtDocumentEntity(
      masterDefendantId = UUID.randomUUID(),
      hmctsCourtDocumentId = UUID.randomUUID(),
      prisonDocumentId = UUID.randomUUID(),
      hmctsCourtHearingId = null,
      prisonEmailAddress = "omu@justice.gov.uk",
      eventType = HmctsEventType.PRISON_COURT_REGISTER_GENERATED,
      courtDocumentType = CourtDocumentType.PRISON_COURT_REGISTER,
      documentGeneratedTimestamp = LocalDateTime.now(),
      ingestionAt = LocalDateTime.now(),
      prisonerNumber = "A1111AA",
    )
  }

  private fun hearing() = CourtHearingEntity(
    hmctsCourtId = UUID.randomUUID(),
    courtName = "Leeds Crown Court",
    hearingType = "First hearing",
    hearingDate = LocalDate.now(),
    hmctsCourtHearingId = UUID.randomUUID(),
    courtDocuments = mutableListOf(),
    courtCharges = mutableListOf(),
    nextCourtHearings = mutableListOf(),
  )

  @Test
  fun `documents on one hearing are one entry, with the newest arrival as its time`() {
    val onOneHearing = hearing()
    val (earlier, later) = documents(2)
    earlier.apply {
      courtHearing = onOneHearing
      downloadedFileSha256 = "file-1"
      ingestionAt = LocalDateTime.now().minusHours(2)
    }
    later.apply {
      courtHearing = onOneHearing
      downloadedFileSha256 = "file-2"
      ingestionAt = LocalDateTime.now().minusHours(1)
    }
    whenever(prisonerSearchService.getPrisonerNumbersInPrison("LEI")).thenReturn(listOf("A1111AA"))
    whenever(
      courtDocumentRepository.findByPrisonerNumberInAndIngestionAtGreaterThanEqualAndIngestionAtLessThanOrderByIngestionAtDesc(
        any(),
        any(),
        any(),
      ),
    ).thenReturn(listOf(later, earlier))

    val day = service.day("LEI", LocalDate.now())

    assertThat(day.hearings).hasSize(1)
    assertThat(day.hearings.single().documents).hasSize(2)
    assertThat(day.hearings.single().courtName).isEqualTo("Leeds Crown Court")
    assertThat(day.hearings.single().receivedAt).isEqualTo(later.ingestionAt)
    assertThat(day.documentsWithoutAHearing).isEmpty()
  }

  @Test
  fun `a document with no hearing is kept apart from the hearings`() {
    whenever(prisonerSearchService.getPrisonerNumbersInPrison("LEI")).thenReturn(listOf("A1111AA"))
    whenever(
      courtDocumentRepository.findByPrisonerNumberInAndIngestionAtGreaterThanEqualAndIngestionAtLessThanOrderByIngestionAtDesc(
        any(),
        any(),
        any(),
      ),
    ).thenReturn(documents(1))

    val day = service.day("LEI", LocalDate.now())

    assertThat(day.hearings).isEmpty()
    assertThat(day.documentsWithoutAHearing).hasSize(1)
  }

  @Test
  fun `lists the week when it is small enough to read`() {
    whenever(prisonerSearchService.getPrisonerNumbersInPrison("LEI")).thenReturn(listOf("A1111AA"))
    whenever(courtDocumentRepository.findByPrisonerNumberInAndIngestionAtGreaterThanEqualAndIngestionAtLessThanOrderByIngestionAtDesc(any(), any(), any())).thenReturn(documents(100))

    assertThat(service.week("LEI", LocalDate.now()).documents).hasSize(100)
  }

  @Test
  fun `gives counts only when the week is too big to list`() {
    whenever(prisonerSearchService.getPrisonerNumbersInPrison("LEI")).thenReturn(listOf("A1111AA"))
    whenever(courtDocumentRepository.findByPrisonerNumberInAndIngestionAtGreaterThanEqualAndIngestionAtLessThanOrderByIngestionAtDesc(any(), any(), any())).thenReturn(documents(101))

    val week = service.week("LEI", LocalDate.now())

    assertThat(week.documents).isNull()
    assertThat(week.totalDocuments).isEqualTo(101)
  }

  @Test
  fun `repeat copies are dropped, following a chain of shared hashes, keeping the oldest`() {
    val (a, b, c) = documents(3)
    a.apply {
      extractedTextSha256 = "text-1"
      downloadedFileSha256 = "file-1"
      ingestionAt = LocalDateTime.now().minusHours(3)
    }
    b.apply {
      extractedTextSha256 = "text-1"
      downloadedFileSha256 = "file-2"
      ingestionAt = LocalDateTime.now().minusHours(2)
    }
    c.apply {
      extractedTextSha256 = "text-3"
      downloadedFileSha256 = "file-2"
      ingestionAt = LocalDateTime.now().minusHours(1)
    }
    whenever(prisonerSearchService.getPrisonerNumbersInPrison("LEI")).thenReturn(listOf("A1111AA"))
    whenever(
      courtDocumentRepository.findByPrisonerNumberInAndIngestionAtGreaterThanEqualAndIngestionAtLessThanOrderByIngestionAtDesc(any(), any(), any()),
    ).thenReturn(listOf(c, b, a))

    val week = service.week("LEI", LocalDate.now())

    assertThat(week.totalDocuments).isEqualTo(1)
    assertThat(week.documents!!.single().prisonDocumentId).isEqualTo(a.prisonDocumentId)
  }

  @Test
  fun `documents with no hash are never treated as copies of each other`() {
    whenever(prisonerSearchService.getPrisonerNumbersInPrison("LEI")).thenReturn(listOf("A1111AA"))
    whenever(
      courtDocumentRepository.findByPrisonerNumberInAndIngestionAtGreaterThanEqualAndIngestionAtLessThanOrderByIngestionAtDesc(any(), any(), any()),
    ).thenReturn(documents(2))

    assertThat(service.week("LEI", LocalDate.now()).totalDocuments).isEqualTo(2)
  }
}
