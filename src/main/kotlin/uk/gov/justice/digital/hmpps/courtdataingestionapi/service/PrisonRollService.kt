package uk.gov.justice.digital.hmpps.courtdataingestionapi.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.PrisonerSearchApiClient
import uk.gov.justice.digital.hmpps.courtdataingestionapi.config.CacheConfiguration.Companion.PRISON_ROLL
import java.time.LocalDateTime

data class PrisonRoll(
  val prisonCode: String,
  val prisonerNumbers: List<String>,
  val takenAt: LocalDateTime,
)

@Service
class PrisonRollService(
  private val prisonerSearchApiClient: PrisonerSearchApiClient,
  @Value("\${prison-roll.page-size:1000}") private val pageSize: Int,
) {

  @Cacheable(PRISON_ROLL)
  fun rollFor(prisonCode: String): PrisonRoll {
    val prisonerNumbers = mutableListOf<String>()
    var page = 0

    do {
      val response = prisonerSearchApiClient.getRoll(prisonCode, page, pageSize)
      prisonerNumbers += response.content.map { it.prisonerNumber }
      page++
    } while (!response.last && page < MAX_PAGES)

    log.info("Roll for {} is {} people", prisonCode, prisonerNumbers.size)
    return PrisonRoll(prisonCode, prisonerNumbers, LocalDateTime.now())
  }

  @CacheEvict(PRISON_ROLL)
  fun refresh(prisonCode: String) {
    log.info("Dropped the cached roll for {}", prisonCode)
  }

  private companion object {
    const val MAX_PAGES = 10
    private val log = LoggerFactory.getLogger(PrisonRollService::class.java)
  }
}
