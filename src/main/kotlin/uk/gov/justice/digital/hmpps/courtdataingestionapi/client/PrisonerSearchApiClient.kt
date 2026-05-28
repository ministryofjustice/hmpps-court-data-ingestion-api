package uk.gov.justice.digital.hmpps.courtdataingestionapi.client

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.prisonersearch.Prisoner


@Component
class PrisonerSearchApiClient(@Qualifier("prisonerSearchApiWebClient") private val webClient: WebClient) {

  fun getByPrisonerNumber(prisonerNumber: String): Prisoner {
    log.info("Getting prisoner info by ID {}", prisonerNumber)
    return webClient
      .get()
      .uri("/prisoner/$prisonerNumber")
      .retrieve()
      .bodyToMono<Prisoner>()
      .block()!!
  }

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
