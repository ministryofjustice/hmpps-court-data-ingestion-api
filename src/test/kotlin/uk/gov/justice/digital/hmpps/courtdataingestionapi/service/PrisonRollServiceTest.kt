package uk.gov.justice.digital.hmpps.courtdataingestionapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.PrisonerSearchApiClient
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.prisonersearch.Prisoner
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.prisonersearch.PrisonerPage
import java.time.LocalDateTime

private const val PRISON = "LEI"

class PrisonRollServiceTest {

  private val prisonerSearchApiClient: PrisonerSearchApiClient = mock()
  private val service = PrisonRollService(prisonerSearchApiClient, pageSize = 1000)

  private fun page(vararg prisonerNumbers: String, last: Boolean = true) = PrisonerPage(content = prisonerNumbers.map { Prisoner(prisonerNumber = it, prisonId = PRISON) }, last = last)

  @Test
  fun `returns the prisoner numbers held at the prison`() {
    whenever(prisonerSearchApiClient.getRoll(eq(PRISON), any(), any())).thenReturn(page("A1111AA", "A2222AA"))

    val roll = service.rollFor(PRISON)

    assertThat(roll.prisonerNumbers).containsExactly("A1111AA", "A2222AA")
    assertThat(roll.prisonCode).isEqualTo(PRISON)
  }

  @Test
  fun `pages until the last page, because a large establishment does not fit in one response`() {
    whenever(prisonerSearchApiClient.getRoll(PRISON, 0, 1000)).thenReturn(page("A1111AA", last = false))
    whenever(prisonerSearchApiClient.getRoll(PRISON, 1, 1000)).thenReturn(page("A2222AA"))

    assertThat(service.rollFor(PRISON).prisonerNumbers).containsExactly("A1111AA", "A2222AA")
  }

  @Test
  fun `stops paging at the cap, so a response that never sets last cannot loop`() {
    whenever(prisonerSearchApiClient.getRoll(eq(PRISON), any(), any())).thenReturn(page("A1111AA", last = false))

    service.rollFor(PRISON)

    verify(prisonerSearchApiClient, times(10)).getRoll(eq(PRISON), any(), any())
  }

  @Test
  fun `records when the roll was taken, which the screen shows to the user`() {
    whenever(prisonerSearchApiClient.getRoll(eq(PRISON), any(), any())).thenReturn(page("A1111AA"))
    val before = LocalDateTime.now()

    assertThat(service.rollFor(PRISON).takenAt).isAfterOrEqualTo(before)
  }

  @Test
  fun `an empty establishment returns an empty roll rather than failing`() {
    whenever(prisonerSearchApiClient.getRoll(eq(PRISON), any(), any())).thenReturn(page())

    assertThat(service.rollFor(PRISON).prisonerNumbers).isEmpty()
  }
}
