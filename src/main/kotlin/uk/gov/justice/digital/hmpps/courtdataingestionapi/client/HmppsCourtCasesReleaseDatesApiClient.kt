package uk.gov.justice.digital.hmpps.courtdataingestionapi.client

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class HmppsCourtCasesReleaseDatesApiClient(@Qualifier("hmppsCourtCasesReleaseDatesApiWebClient") private val webClient: WebClient) {

  fun deleteThingsToDoCache(prisonerNumber: String) {
    log.info("Deleting things to do cache for $prisonerNumber")
    webClient
      .delete()
      .uri("/things-to-do/prisoner/$prisonerNumber/evict")
      .retrieve()
      .toBodilessEntity()
      .block()
  }

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
