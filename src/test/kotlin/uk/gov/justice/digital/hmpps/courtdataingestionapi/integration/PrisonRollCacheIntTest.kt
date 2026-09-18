package uk.gov.justice.digital.hmpps.courtdataingestionapi.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.CacheManager
import org.springframework.test.context.bean.override.mockito.MockitoBean
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.PrisonerSearchApiClient
import uk.gov.justice.digital.hmpps.courtdataingestionapi.config.CacheConfiguration
import uk.gov.justice.digital.hmpps.courtdataingestionapi.config.CacheConfiguration.Companion.PRISON_ROLL
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.prisonersearch.Prisoner
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.prisonersearch.PrisonerPage
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.PrisonRollService

private const val PRISON = "LEI"
private const val OTHER_PRISON = "MDI"

class PrisonRollCacheIntTest : IntegrationTestBase() {

  @Autowired
  private lateinit var rollService: PrisonRollService

  @Autowired
  private lateinit var cacheManager: CacheManager

  @Autowired
  private lateinit var cacheConfiguration: CacheConfiguration

  @MockitoBean
  private lateinit var prisonerSearchApiClient: PrisonerSearchApiClient

  @BeforeEach
  fun setUp() {
    cacheManager.getCache(PRISON_ROLL)?.clear()
    whenever(prisonerSearchApiClient.getRoll(any(), any(), any()))
      .thenReturn(PrisonerPage(content = listOf(Prisoner(prisonerNumber = "A1111AA", prisonId = PRISON))))
  }

  @Test
  fun `a second read inside the cache window does not refetch, so paging a week stays cheap`() {
    rollService.rollFor(PRISON)
    rollService.rollFor(PRISON)

    verify(prisonerSearchApiClient, times(1)).getRoll(eq(PRISON), any(), any())
  }

  @Test
  fun `rolls are cached per prison, not globally`() {
    rollService.rollFor(PRISON)
    rollService.rollFor(OTHER_PRISON)

    verify(prisonerSearchApiClient, times(1)).getRoll(eq(PRISON), any(), any())
    verify(prisonerSearchApiClient, times(1)).getRoll(eq(OTHER_PRISON), any(), any())
  }

  @Test
  fun `the cached roll is returned unchanged, including the time it was taken`() {
    val first = rollService.rollFor(PRISON)
    val second = rollService.rollFor(PRISON)

    assertThat(second.takenAt).isEqualTo(first.takenAt)
  }

  @Test
  fun `refresh drops the entry, so the next read takes a fresh roll`() {
    rollService.rollFor(PRISON)
    rollService.refresh(PRISON)
    rollService.rollFor(PRISON)

    verify(prisonerSearchApiClient, times(2)).getRoll(eq(PRISON), any(), any())
  }

  @Test
  fun `refreshing one prison leaves the others cached`() {
    rollService.rollFor(PRISON)
    rollService.rollFor(OTHER_PRISON)
    rollService.refresh(PRISON)
    rollService.rollFor(OTHER_PRISON)

    verify(prisonerSearchApiClient, times(1)).getRoll(eq(OTHER_PRISON), any(), any())
  }

  @Test
  fun `the scheduled sweep clears every prison, so no roll outlives it`() {
    rollService.rollFor(PRISON)
    rollService.rollFor(OTHER_PRISON)

    cacheConfiguration.cacheEvict()

    rollService.rollFor(PRISON)
    rollService.rollFor(OTHER_PRISON)
    verify(prisonerSearchApiClient, times(2)).getRoll(eq(PRISON), any(), any())
    verify(prisonerSearchApiClient, times(2)).getRoll(eq(OTHER_PRISON), any(), any())
  }
}
