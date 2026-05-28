package uk.gov.justice.digital.hmpps.courtdataingestionapi.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.PrisonerSearchApiClient
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.prisonersearch.Prisoner

@Service
@Transactional(readOnly = true)
class PrisonerSearchService(
  private val prisonerSearchApiClient: PrisonerSearchApiClient,
) {

  fun getPrison(prisonerId: String): String? {
    log.debug("Looking up prisoner for {}", prisonerId)
    val prisoner: Prisoner = prisonerSearchApiClient.getByPrisonerNumber(prisonerId)
    return prisoner.prisonId
  }

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
