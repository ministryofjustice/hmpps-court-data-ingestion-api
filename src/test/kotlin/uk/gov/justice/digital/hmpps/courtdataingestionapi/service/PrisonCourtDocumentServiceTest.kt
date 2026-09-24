package uk.gov.justice.digital.hmpps.courtdataingestionapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtDocumentType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.HmctsEventType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.prisonersearch.Prisoner
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtDocumentRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class PrisonCourtDocumentServiceTest {

  private val courtDocumentRepository: CourtDocumentRepository = mock()
  private val prisonerSearchService: PrisonerSearchService = mock()
  private val service = PrisonCourtDocumentService(courtDocumentRepository, prisonerSearchService)

  private fun prisoner(prisonerNumber: String = "A1111AA") = Prisoner(prisonerNumber, "LEI", firstName = "Chappel", lastName = "House")

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

  @Test
  fun `lists the week when it is small enough to read`() {
    whenever(prisonerSearchService.getPrisonersInPrison("LEI")).thenReturn(listOf(prisoner()))
    whenever(courtDocumentRepository.findByPrisonerNumberInAndIngestionAtGreaterThanEqualAndIngestionAtLessThanOrderByIngestionAtDesc(any(), any(), any())).thenReturn(documents(100))

    assertThat(service.week("LEI", LocalDate.now()).documents).hasSize(100)
  }

  @Test
  fun `gives counts only when the week is too big to list`() {
    whenever(prisonerSearchService.getPrisonersInPrison("LEI")).thenReturn(listOf(prisoner()))
    whenever(courtDocumentRepository.findByPrisonerNumberInAndIngestionAtGreaterThanEqualAndIngestionAtLessThanOrderByIngestionAtDesc(any(), any(), any())).thenReturn(documents(101))

    val week = service.week("LEI", LocalDate.now())

    assertThat(week.documents).isNull()
    assertThat(week.totalDocuments).isEqualTo(101)
  }

  @Test
  fun `the day names the people it covers, from the roll`() {
    whenever(prisonerSearchService.getPrisonersInPrison("LEI")).thenReturn(listOf(prisoner()))
    whenever(
      courtDocumentRepository.findByPrisonerNumberInAndIngestionAtGreaterThanEqualAndIngestionAtLessThanOrderByIngestionAtDesc(
        any(),
        any(),
        any(),
      ),
    ).thenReturn(documents(1))

    val person = service.day("LEI", LocalDate.now()).people.single()

    assertThat(person.prisonerNumber).isEqualTo("A1111AA")
    assertThat(person.firstName).isEqualTo("Chappel")
    assertThat(person.lastName).isEqualTo("House")
  }
}
