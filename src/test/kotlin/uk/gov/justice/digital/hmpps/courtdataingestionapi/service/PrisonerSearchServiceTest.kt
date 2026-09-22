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

class PrisonerSearchServiceTest {

  private val prisonerSearchApiClient: PrisonerSearchApiClient = mock()
  private val service = PrisonerSearchService(prisonerSearchApiClient)

  private fun page(vararg prisonerNumbers: String, last: Boolean = true) = PrisonerPage(prisonerNumbers.map { Prisoner(it, "LEI") }, last)

  @Test
  fun `fetches every page of a large prison`() {
    whenever(prisonerSearchApiClient.getPrisonersInPrison("LEI", 0, 1000)).thenReturn(page("A1111AA", last = false))
    whenever(prisonerSearchApiClient.getPrisonersInPrison("LEI", 1, 1000)).thenReturn(page("A2222AA"))

    assertThat(service.getPrisonerNumbersInPrison("LEI")).containsExactly("A1111AA", "A2222AA")
  }

  @Test
  fun `stops at the page cap if the last page is never reported`() {
    whenever(prisonerSearchApiClient.getPrisonersInPrison(eq("LEI"), any(), any())).thenReturn(page("A1111AA", last = false))

    service.getPrisonerNumbersInPrison("LEI")

    verify(prisonerSearchApiClient, times(10)).getPrisonersInPrison(eq("LEI"), any(), any())
  }
}
