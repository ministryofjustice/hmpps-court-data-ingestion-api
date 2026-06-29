package uk.gov.justice.digital.hmpps.courtdataingestionapi.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.PrisonerSearchApiClient
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.prisonersearch.Prisoner

@Service
@Transactional(readOnly = true)
class PrisonerSearchService(
  private val prisonerSearchApiClient: PrisonerSearchApiClient,
) {

  fun getPrison(prisonerId: String): String? {
    log.debug("Looking up prisoner for {}", prisonerId)
    try {
      val prisoner: Prisoner = prisonerSearchApiClient.getByPrisonerNumber(prisonerId)
      return prisoner.prisonId
    } catch (e: WebClientResponseException.NotFound) {
      log.error("Prisoner with ID {} not found, error {}", prisonerId, e.message)
      return null
    }
  }

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
